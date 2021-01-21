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
package org.codelibs.fesen.action.search;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fesen.Version;
import org.codelibs.fesen.action.OriginalIndices;
import org.codelibs.fesen.action.search.SearchPhase;
import org.codelibs.fesen.action.search.SearchPhaseContext;
import org.codelibs.fesen.action.search.SearchRequest;
import org.codelibs.fesen.action.search.SearchResponse;
import org.codelibs.fesen.action.search.SearchShardIterator;
import org.codelibs.fesen.action.search.SearchTask;
import org.codelibs.fesen.action.search.SearchTransportService;
import org.codelibs.fesen.action.search.ShardSearchFailure;
import org.codelibs.fesen.action.search.TransportSearchHelper;
import org.codelibs.fesen.common.Nullable;
import org.codelibs.fesen.common.lease.Releasable;
import org.codelibs.fesen.common.util.concurrent.AtomicArray;
import org.codelibs.fesen.search.SearchPhaseResult;
import org.codelibs.fesen.search.SearchShardTarget;
import org.codelibs.fesen.search.internal.InternalSearchResponse;
import org.codelibs.fesen.search.internal.ShardSearchContextId;
import org.codelibs.fesen.search.internal.ShardSearchRequest;
import org.codelibs.fesen.transport.Transport;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SearchPhaseContext for tests
 */
public final class MockSearchPhaseContext implements SearchPhaseContext {
    private static final Logger logger = LogManager.getLogger(MockSearchPhaseContext.class);
    final AtomicReference<Throwable> phaseFailure = new AtomicReference<>();
    final int numShards;
    final AtomicInteger numSuccess;
    final List<ShardSearchFailure> failures = Collections.synchronizedList(new ArrayList<>());
    SearchTransportService searchTransport;
    final Set<ShardSearchContextId> releasedSearchContexts = new HashSet<>();
    final SearchRequest searchRequest = new SearchRequest();
    final AtomicReference<SearchResponse> searchResponse = new AtomicReference<>();

    public MockSearchPhaseContext(int numShards) {
        this.numShards = numShards;
        numSuccess = new AtomicInteger(numShards);
    }

    public void assertNoFailure() {
        if (phaseFailure.get() != null) {
            throw new AssertionError(phaseFailure.get());
        }
    }

    @Override
    public int getNumShards() {
        return numShards;
    }

    @Override
    public Logger getLogger() {
        return logger;
    }

    @Override
    public SearchTask getTask() {
        return new SearchTask(0, "n/a", "n/a", () -> "test", null, Collections.emptyMap());
    }

    @Override
    public SearchRequest getRequest() {
        return searchRequest;
    }

    @Override
    public void sendSearchResponse(InternalSearchResponse internalSearchResponse, AtomicArray<SearchPhaseResult> queryResults) {
        String scrollId = getRequest().scroll() != null ? TransportSearchHelper.buildScrollId(queryResults, Version.CURRENT) : null;
        String searchContextId =
            getRequest().pointInTimeBuilder() != null ? TransportSearchHelper.buildScrollId(queryResults, Version.CURRENT) : null;
        searchResponse.set(new SearchResponse(internalSearchResponse, scrollId, numShards, numSuccess.get(), 0, 0,
            failures.toArray(ShardSearchFailure.EMPTY_ARRAY), SearchResponse.Clusters.EMPTY, searchContextId));
    }

    @Override
    public void onPhaseFailure(SearchPhase phase, String msg, Throwable cause) {
        phaseFailure.set(cause);
    }

    @Override
    public void onShardFailure(int shardIndex, @Nullable SearchShardTarget shardTarget, Exception e) {
        failures.add(new ShardSearchFailure(e, shardTarget));
        numSuccess.decrementAndGet();
    }

    @Override
    public Transport.Connection getConnection(String clusterAlias, String nodeId) {
        return null; // null is ok here for this test
    }

    @Override
    public SearchTransportService getSearchTransport() {
        Assert.assertNotNull(searchTransport);
        return searchTransport;
    }

    @Override
    public ShardSearchRequest buildShardSearchRequest(SearchShardIterator shardIt) {
        Assert.fail("should not be called");
        return null;
    }

    @Override
    public void executeNextPhase(SearchPhase currentPhase, SearchPhase nextPhase) {
        try {
            nextPhase.run();
        } catch (Exception e) {
           onPhaseFailure(nextPhase, "phase failed", e);
        }
    }

    @Override
    public void addReleasable(Releasable releasable) {
        // Noop
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }

    @Override
    public void onFailure(Exception e) {
        Assert.fail("should not be called");
    }

    @Override
    public void sendReleaseSearchContext(ShardSearchContextId contextId, Transport.Connection connection, OriginalIndices originalIndices) {
        releasedSearchContexts.add(contextId);
    }
}