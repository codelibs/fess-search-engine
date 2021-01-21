/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.codelibs.fesen.index.reindex;

import org.codelibs.fesen.action.ActionFuture;
import org.codelibs.fesen.action.admin.cluster.node.info.NodeInfo;
import org.codelibs.fesen.action.admin.cluster.node.tasks.list.ListTasksResponse;
import org.codelibs.fesen.action.bulk.BackoffPolicy;
import org.codelibs.fesen.action.bulk.BulkRequestBuilder;
import org.codelibs.fesen.action.bulk.BulkResponse;
import org.codelibs.fesen.action.bulk.Retry;
import org.codelibs.fesen.client.Client;
import org.codelibs.fesen.common.bytes.BytesArray;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.common.transport.TransportAddress;
import org.codelibs.fesen.common.util.concurrent.EsRejectedExecutionException;
import org.codelibs.fesen.http.HttpInfo;
import org.codelibs.fesen.index.query.QueryBuilders;
import org.codelibs.fesen.index.reindex.AbstractBulkByScrollRequestBuilder;
import org.codelibs.fesen.index.reindex.BulkByScrollResponse;
import org.codelibs.fesen.index.reindex.BulkByScrollTask;
import org.codelibs.fesen.index.reindex.DeleteByQueryAction;
import org.codelibs.fesen.index.reindex.DeleteByQueryRequestBuilder;
import org.codelibs.fesen.index.reindex.ReindexAction;
import org.codelibs.fesen.index.reindex.ReindexPlugin;
import org.codelibs.fesen.index.reindex.ReindexRequestBuilder;
import org.codelibs.fesen.index.reindex.RemoteInfo;
import org.codelibs.fesen.index.reindex.TransportReindexAction;
import org.codelibs.fesen.index.reindex.UpdateByQueryAction;
import org.codelibs.fesen.index.reindex.UpdateByQueryRequestBuilder;
import org.codelibs.fesen.plugins.Plugin;
import org.codelibs.fesen.test.ESIntegTestCase;
import org.codelibs.fesen.threadpool.ThreadPool;
import org.codelibs.fesen.transport.Netty4Plugin;
import org.junit.After;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.function.Function;

import static java.util.Collections.emptyMap;
import static org.codelibs.fesen.index.reindex.ReindexTestCase.matcher;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;

/**
 * Integration test for bulk retry behavior. Useful because retrying relies on the way that the
 * rest of Elasticsearch throws exceptions and unit tests won't verify that.
 */
public class RetryTests extends ESIntegTestCase {

    private static final int DOC_COUNT = 20;

    private List<CyclicBarrier> blockedExecutors = new ArrayList<>();

    @After
    public void forceUnblockAllExecutors() {
        for (CyclicBarrier barrier: blockedExecutors) {
            barrier.reset();
        }
    }

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return Arrays.asList(
                ReindexPlugin.class,
                Netty4Plugin.class);
    }

    @Override
    protected Collection<Class<? extends Plugin>> transportClientPlugins() {
        return Arrays.asList(
                ReindexPlugin.class,
                Netty4Plugin.class);
    }

    /**
     * Lower the queue sizes to be small enough that bulk will time out and have to be retried.
     */
    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder().put(super.nodeSettings(nodeOrdinal)).put(nodeSettings()).build();
    }

    @Override
    protected boolean addMockHttpTransport() {
        return false; // enable HTTP so we can test retries on reindex from remote; in this case the "remote" cluster is just this cluster
    }

    final Settings nodeSettings() {
        return Settings.builder()
                // whitelist reindexing from the HTTP host we're going to use
                .put(TransportReindexAction.REMOTE_CLUSTER_WHITELIST.getKey(), "127.0.0.1:*")
                .build();
    }

    public void testReindex() throws Exception {
        testCase(
                ReindexAction.NAME,
                client -> new ReindexRequestBuilder(client, ReindexAction.INSTANCE).source("source").destination("dest"),
                matcher().created(DOC_COUNT));
    }

    public void testReindexFromRemote() throws Exception {
        Function<Client, AbstractBulkByScrollRequestBuilder<?, ?>> function = client -> {
            /*
             * Use the master node for the reindex from remote because that node
             * doesn't have a copy of the data on it.
             */
            NodeInfo masterNode = null;
            for (NodeInfo candidate : client.admin().cluster().prepareNodesInfo().get().getNodes()) {
                if (candidate.getNode().isMasterNode()) {
                    masterNode = candidate;
                }
            }
            assertNotNull(masterNode);

            TransportAddress address = masterNode.getInfo(HttpInfo.class).getAddress().publishAddress();
            RemoteInfo remote =
                new RemoteInfo("http", address.getAddress(), address.getPort(), null,
                    new BytesArray("{\"match_all\":{}}"), null, null, emptyMap(),
                    RemoteInfo.DEFAULT_SOCKET_TIMEOUT, RemoteInfo.DEFAULT_CONNECT_TIMEOUT);
            ReindexRequestBuilder request = new ReindexRequestBuilder(client, ReindexAction.INSTANCE).source("source").destination("dest")
                    .setRemoteInfo(remote);
            return request;
        };
        testCase(ReindexAction.NAME, function, matcher().created(DOC_COUNT));
    }

    public void testUpdateByQuery() throws Exception {
        testCase(UpdateByQueryAction.NAME, client -> new UpdateByQueryRequestBuilder(client, UpdateByQueryAction.INSTANCE).source("source"),
                matcher().updated(DOC_COUNT));
    }

    public void testDeleteByQuery() throws Exception {
        testCase(DeleteByQueryAction.NAME, client -> new DeleteByQueryRequestBuilder(client, DeleteByQueryAction.INSTANCE).source("source")
                .filter(QueryBuilders.matchAllQuery()), matcher().deleted(DOC_COUNT));
    }

    private void testCase(
            String action,
            Function<Client, AbstractBulkByScrollRequestBuilder<?, ?>> request,
            BulkIndexByScrollResponseMatcher matcher)
            throws Exception {
        /*
         * These test cases work by stuffing the bulk queue of a single node and
         * making sure that we read and write from that node.
         */

        final Settings nodeSettings = Settings.builder()
                // use pools of size 1 so we can block them
                .put("thread_pool.write.size", 1)
                // use queues of size 1 because size 0 is broken and because bulk requests need the queue to function
                .put("thread_pool.write.queue_size", 1)
                .put("node.attr.color", "blue")
                .build();
        final String node = internalCluster().startDataOnlyNode(nodeSettings);
        final Settings indexSettings =
                Settings.builder()
                        .put("index.number_of_shards", 1)
                        .put("index.number_of_replicas", 0)
                        .put("index.routing.allocation.include.color", "blue")
                        .build();

        // Create the source index on the node with small thread pools so we can block them.
        client().admin().indices().prepareCreate("source").setSettings(indexSettings).execute().actionGet();
        // Not all test cases use the dest index but those that do require that it be on the node will small thread pools
        client().admin().indices().prepareCreate("dest").setSettings(indexSettings).execute().actionGet();
        // Build the test data. Don't use indexRandom because that won't work consistently with such small thread pools.
        BulkRequestBuilder bulk = client().prepareBulk();
        for (int i = 0; i < DOC_COUNT; i++) {
            bulk.add(client().prepareIndex("source", "test").setSource("foo", "bar " + i));
        }

        Retry retry = new Retry(BackoffPolicy.exponentialBackoff(), client().threadPool());
        BulkResponse initialBulkResponse = retry.withBackoff(client()::bulk, bulk.request()).actionGet();
        assertFalse(initialBulkResponse.buildFailureMessage(), initialBulkResponse.hasFailures());
        client().admin().indices().prepareRefresh("source").get();

        AbstractBulkByScrollRequestBuilder<?, ?> builder = request.apply(internalCluster().masterClient());
        // Make sure we use more than one batch so we have to scroll
        builder.source().setSize(DOC_COUNT / randomIntBetween(2, 10));

        logger.info("Blocking bulk so we start to get bulk rejections");
        CyclicBarrier bulkBlock = blockExecutor(ThreadPool.Names.WRITE, node);

        logger.info("Starting request");
        ActionFuture<BulkByScrollResponse> responseListener = builder.execute();

        try {
            logger.info("Waiting for bulk rejections");
            assertBusy(() -> assertThat(taskStatus(action).getBulkRetries(), greaterThan(0L)));
            bulkBlock.await();

            logger.info("Waiting for the request to finish");
            BulkByScrollResponse response = responseListener.get();
            assertThat(response, matcher);
            assertThat(response.getBulkRetries(), greaterThan(0L));
        } finally {
            // Fetch the response just in case we blew up half way through. This will make sure the failure is thrown up to the top level.
            BulkByScrollResponse response = responseListener.get();
            assertThat(response.getSearchFailures(), empty());
            assertThat(response.getBulkFailures(), empty());
        }
    }

    /**
     * Blocks the named executor by getting its only thread running a task blocked on a CyclicBarrier and fills the queue with a noop task.
     * So requests to use this queue should get {@link EsRejectedExecutionException}s.
     */
    private CyclicBarrier blockExecutor(String name, String node) throws Exception {
        ThreadPool threadPool = internalCluster().getInstance(ThreadPool.class, node);
        CyclicBarrier barrier = new CyclicBarrier(2);
        logger.info("Blocking the [{}] executor", name);
        threadPool.executor(name).execute(() -> {
            try {
                threadPool.executor(name).execute(() -> {});
                barrier.await();
                logger.info("Blocked the [{}] executor", name);
                barrier.await();
                logger.info("Unblocking the [{}] executor", name);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        barrier.await();
        blockedExecutors.add(barrier);
        return barrier;
    }

    /**
     * Fetch the status for a task of type "action". Fails if there aren't exactly one of that type of task running.
     */
    private BulkByScrollTask.Status taskStatus(String action) {
        /*
         * We always use the master client because we always start the test requests on the
         * master. We do this simply to make sure that the test request is not started on the
         * node who's queue we're manipulating.
         */
        ListTasksResponse response = client().admin().cluster().prepareListTasks().setActions(action).setDetailed(true).get();
        assertThat(response.getTasks(), hasSize(1));
        return (BulkByScrollTask.Status) response.getTasks().get(0).getStatus();
    }

}