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

package org.codelibs.fesen.test;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fesen.action.admin.cluster.node.info.NodeInfo;
import org.codelibs.fesen.action.admin.cluster.node.info.NodesInfoResponse;
import org.codelibs.fesen.action.admin.cluster.node.stats.NodeStats;
import org.codelibs.fesen.action.admin.cluster.node.stats.NodesStatsResponse;
import org.codelibs.fesen.client.Client;
import org.codelibs.fesen.cluster.node.DiscoveryNode;
import org.codelibs.fesen.common.breaker.CircuitBreaker;
import org.codelibs.fesen.common.io.stream.NamedWriteableRegistry;
import org.codelibs.fesen.common.network.NetworkModule;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.common.transport.TransportAddress;
import org.codelibs.fesen.env.Environment;
import org.codelibs.fesen.http.HttpInfo;
import org.codelibs.fesen.plugins.Plugin;
import org.codelibs.fesen.transport.MockTransportClient;
import org.codelibs.fesen.transport.TransportSettings;
import org.codelibs.fesen.transport.nio.MockNioTransportPlugin;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.codelibs.fesen.action.admin.cluster.node.info.NodesInfoRequest.Metric.HTTP;
import static org.codelibs.fesen.action.admin.cluster.node.info.NodesInfoRequest.Metric.SETTINGS;
import static org.codelibs.fesen.action.admin.cluster.node.stats.NodesStatsRequest.Metric.BREAKER;
import static org.codelibs.fesen.test.ESTestCase.getTestTransportType;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

/**
 * External cluster to run the tests against.
 * It is a pure immutable test cluster that allows to send requests to a pre-existing cluster
 * and supports by nature all the needed test operations like wipeIndices etc.
 */
public final class ExternalTestCluster extends TestCluster {

    private static final Logger logger = LogManager.getLogger(ExternalTestCluster.class);

    private static final AtomicInteger counter = new AtomicInteger();
    public static final String EXTERNAL_CLUSTER_PREFIX = "external_";

    private final MockTransportClient client;

    private final InetSocketAddress[] httpAddresses;

    private final String clusterName;

    private final int numDataNodes;
    private final int numMasterAndDataNodes;

    public ExternalTestCluster(Path tempDir, Settings additionalSettings, Collection<Class<? extends Plugin>> pluginClasses,
                               TransportAddress... transportAddresses) {
        super(0);
        Settings.Builder clientSettingsBuilder = Settings.builder()
            .put(additionalSettings)
            .put("node.name", InternalTestCluster.TRANSPORT_CLIENT_PREFIX + EXTERNAL_CLUSTER_PREFIX + counter.getAndIncrement())
            .put("client.transport.ignore_cluster_name", true)
            .put(TransportSettings.PORT.getKey(), ESTestCase.getPortRange())
            .put(Environment.PATH_HOME_SETTING.getKey(), tempDir);
        boolean addMockTcpTransport = additionalSettings.get(NetworkModule.TRANSPORT_TYPE_KEY) == null;

        if (addMockTcpTransport) {
            String transport = getTestTransportType();
            clientSettingsBuilder.put(NetworkModule.TRANSPORT_TYPE_KEY, transport);
            if (pluginClasses.contains(MockNioTransportPlugin.class) == false) {
                pluginClasses = new ArrayList<>(pluginClasses);
                pluginClasses.add(MockNioTransportPlugin.class);
            }
        }
        Settings clientSettings = clientSettingsBuilder.build();
        MockTransportClient client = new MockTransportClient(clientSettings, pluginClasses);
        try {
            client.addTransportAddresses(transportAddresses);
            NodesInfoResponse nodeInfos = client.admin().cluster().prepareNodesInfo().clear()
                .addMetrics(SETTINGS.metricName(), HTTP.metricName())
                .get();
            httpAddresses = new InetSocketAddress[nodeInfos.getNodes().size()];
            this.clusterName = nodeInfos.getClusterName().value();
            int dataNodes = 0;
            int masterAndDataNodes = 0;
            for (int i = 0; i < nodeInfos.getNodes().size(); i++) {
                NodeInfo nodeInfo = nodeInfos.getNodes().get(i);
                httpAddresses[i] = nodeInfo.getInfo(HttpInfo.class).address().publishAddress().address();
                if (DiscoveryNode.isDataNode(nodeInfo.getSettings())) {
                    dataNodes++;
                    masterAndDataNodes++;
                } else if (DiscoveryNode.isMasterNode(nodeInfo.getSettings())) {
                    masterAndDataNodes++;
                }
            }
            this.numDataNodes = dataNodes;
            this.numMasterAndDataNodes = masterAndDataNodes;
            this.client = client;

            logger.info("Setup ExternalTestCluster [{}] made of [{}] nodes", nodeInfos.getClusterName().value(), size());
        } catch (Exception e) {
            client.close();
            throw e;
        }
    }

    @Override
    public void afterTest() {

    }

    @Override
    public Client client() {
        return client;
    }

    @Override
    public int size() {
        return httpAddresses.length;
    }

    @Override
    public int numDataNodes() {
        return numDataNodes;
    }

    @Override
    public int numDataAndMasterNodes() {
        return numMasterAndDataNodes;
    }

    @Override
    public InetSocketAddress[] httpAddresses() {
        return httpAddresses;
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    @Override
    public void ensureEstimatedStats() {
        if (size() > 0) {
            NodesStatsResponse nodeStats = client().admin().cluster().prepareNodesStats().clear()
                .setIndices(true)
                .addMetric(BREAKER.metricName())
                .execute().actionGet();
            for (NodeStats stats : nodeStats.getNodes()) {
                assertThat("Fielddata breaker not reset to 0 on node: " + stats.getNode(),
                        stats.getBreaker().getStats(CircuitBreaker.FIELDDATA).getEstimated(), equalTo(0L));
                assertThat("Accounting breaker not reset to " + stats.getIndices().getSegments().getMemoryInBytes() +
                                " on node: " + stats.getNode(),
                        stats.getBreaker().getStats(CircuitBreaker.ACCOUNTING).getEstimated(),
                        equalTo(stats.getIndices().getSegments().getMemoryInBytes()));
                // ExternalTestCluster does not check the request breaker,
                // because checking it requires a network request, which in
                // turn increments the breaker, making it non-0

                assertThat("Fielddata size must be 0 on node: " +
                    stats.getNode(), stats.getIndices().getFieldData().getMemorySizeInBytes(), equalTo(0L));
                assertThat("Query cache size must be 0 on node: " +
                    stats.getNode(), stats.getIndices().getQueryCache().getMemorySizeInBytes(), equalTo(0L));
                assertThat("FixedBitSet cache size must be 0 on node: " +
                    stats.getNode(), stats.getIndices().getSegments().getBitsetMemoryInBytes(), equalTo(0L));
            }
        }
    }

    @Override
    public Iterable<Client> getClients() {
        return Collections.singleton(client);
    }

    @Override
    public NamedWriteableRegistry getNamedWriteableRegistry() {
        return client.getNamedWriteableRegistry();
    }

    @Override
    public String getClusterName() {
        return clusterName;
    }
}
