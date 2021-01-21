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

package org.codelibs.fesen.action.admin.indices.cache.clear;

import java.io.IOException;
import java.util.List;

import org.codelibs.fesen.action.support.ActionFilters;
import org.codelibs.fesen.action.support.DefaultShardOperationFailedException;
import org.codelibs.fesen.action.support.broadcast.node.TransportBroadcastByNodeAction;
import org.codelibs.fesen.cluster.ClusterState;
import org.codelibs.fesen.cluster.block.ClusterBlockException;
import org.codelibs.fesen.cluster.block.ClusterBlockLevel;
import org.codelibs.fesen.cluster.metadata.IndexNameExpressionResolver;
import org.codelibs.fesen.cluster.routing.ShardRouting;
import org.codelibs.fesen.cluster.routing.ShardsIterator;
import org.codelibs.fesen.cluster.service.ClusterService;
import org.codelibs.fesen.common.inject.Inject;
import org.codelibs.fesen.common.io.stream.StreamInput;
import org.codelibs.fesen.indices.IndicesService;
import org.codelibs.fesen.threadpool.ThreadPool;
import org.codelibs.fesen.transport.TransportService;


/**
 * Indices clear cache action.
 */
public class TransportClearIndicesCacheAction extends TransportBroadcastByNodeAction<ClearIndicesCacheRequest, ClearIndicesCacheResponse,
    TransportBroadcastByNodeAction.EmptyResult> {

    private final IndicesService indicesService;

    @Inject
    public TransportClearIndicesCacheAction(ClusterService clusterService, TransportService transportService,
                                            IndicesService indicesService, ActionFilters actionFilters,
                                            IndexNameExpressionResolver indexNameExpressionResolver) {
        super(ClearIndicesCacheAction.NAME, clusterService, transportService, actionFilters,
            indexNameExpressionResolver, ClearIndicesCacheRequest::new, ThreadPool.Names.MANAGEMENT, false);
        this.indicesService = indicesService;
    }

    @Override
    protected EmptyResult readShardResult(StreamInput in) throws IOException {
        return EmptyResult.readEmptyResultFrom(in);
    }

    @Override
    protected ClearIndicesCacheResponse newResponse(ClearIndicesCacheRequest request, int totalShards, int successfulShards,
                                                    int failedShards, List<EmptyResult> responses,
                                                    List<DefaultShardOperationFailedException> shardFailures, ClusterState clusterState) {
        return new ClearIndicesCacheResponse(totalShards, successfulShards, failedShards, shardFailures);
    }

    @Override
    protected ClearIndicesCacheRequest readRequestFrom(StreamInput in) throws IOException {
        return new ClearIndicesCacheRequest(in);
    }

    @Override
    protected EmptyResult shardOperation(ClearIndicesCacheRequest request, ShardRouting shardRouting) {
        indicesService.clearIndexShardCache(shardRouting.shardId(), request.queryCache(), request.fieldDataCache(), request.requestCache(),
            request.fields());
        return EmptyResult.INSTANCE;
    }

    /**
     * The refresh request works against *all* shards.
     */
    @Override
    protected ShardsIterator shards(ClusterState clusterState, ClearIndicesCacheRequest request, String[] concreteIndices) {
        return clusterState.routingTable().allShards(concreteIndices);
    }

    @Override
    protected ClusterBlockException checkGlobalBlock(ClusterState state, ClearIndicesCacheRequest request) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected ClusterBlockException checkRequestBlock(ClusterState state, ClearIndicesCacheRequest request, String[] concreteIndices) {
        return state.blocks().indicesBlockedException(ClusterBlockLevel.METADATA_WRITE, concreteIndices);
    }
}