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

package org.codelibs.fesen.action.admin.cluster.allocation;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codelibs.fesen.action.ActionListener;
import org.codelibs.fesen.action.support.ActionFilters;
import org.codelibs.fesen.action.support.master.TransportMasterNodeAction;
import org.codelibs.fesen.cluster.ClusterInfo;
import org.codelibs.fesen.cluster.ClusterInfoService;
import org.codelibs.fesen.cluster.ClusterState;
import org.codelibs.fesen.cluster.block.ClusterBlockException;
import org.codelibs.fesen.cluster.block.ClusterBlockLevel;
import org.codelibs.fesen.cluster.metadata.IndexNameExpressionResolver;
import org.codelibs.fesen.cluster.node.DiscoveryNode;
import org.codelibs.fesen.cluster.routing.RoutingNodes;
import org.codelibs.fesen.cluster.routing.ShardRouting;
import org.codelibs.fesen.cluster.routing.allocation.AllocationService;
import org.codelibs.fesen.cluster.routing.allocation.RoutingAllocation;
import org.codelibs.fesen.cluster.routing.allocation.ShardAllocationDecision;
import org.codelibs.fesen.cluster.routing.allocation.RoutingAllocation.DebugMode;
import org.codelibs.fesen.cluster.routing.allocation.allocator.ShardsAllocator;
import org.codelibs.fesen.cluster.routing.allocation.decider.AllocationDeciders;
import org.codelibs.fesen.cluster.service.ClusterService;
import org.codelibs.fesen.common.inject.Inject;
import org.codelibs.fesen.common.io.stream.StreamInput;
import org.codelibs.fesen.snapshots.SnapshotsInfoService;
import org.codelibs.fesen.threadpool.ThreadPool;
import org.codelibs.fesen.transport.TransportService;

import java.io.IOException;
import java.util.List;

/**
 * The {@code TransportClusterAllocationExplainAction} is responsible for actually executing the explanation of a shard's allocation on the
 * master node in the cluster.
 */
public class TransportClusterAllocationExplainAction
        extends TransportMasterNodeAction<ClusterAllocationExplainRequest, ClusterAllocationExplainResponse> {

    private static final Logger logger = LogManager.getLogger(TransportClusterAllocationExplainAction.class);

    private final ClusterInfoService clusterInfoService;
    private final SnapshotsInfoService snapshotsInfoService;
    private final AllocationDeciders allocationDeciders;
    private final ShardsAllocator shardAllocator;
    private final AllocationService allocationService;

    @Inject
    public TransportClusterAllocationExplainAction(TransportService transportService, ClusterService clusterService,
                                                   ThreadPool threadPool, ActionFilters actionFilters,
                                                   IndexNameExpressionResolver indexNameExpressionResolver,
                                                   ClusterInfoService clusterInfoService, SnapshotsInfoService snapshotsInfoService,
                                                   AllocationDeciders allocationDeciders,
                                                   ShardsAllocator shardAllocator, AllocationService allocationService) {
        super(ClusterAllocationExplainAction.NAME, transportService, clusterService, threadPool, actionFilters,
            ClusterAllocationExplainRequest::new, indexNameExpressionResolver);
        this.clusterInfoService = clusterInfoService;
        this.snapshotsInfoService = snapshotsInfoService;
        this.allocationDeciders = allocationDeciders;
        this.shardAllocator = shardAllocator;
        this.allocationService = allocationService;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.MANAGEMENT;
    }

    @Override
    protected ClusterAllocationExplainResponse read(StreamInput in) throws IOException {
        return new ClusterAllocationExplainResponse(in);
    }

    @Override
    protected ClusterBlockException checkBlock(ClusterAllocationExplainRequest request, ClusterState state) {
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_READ);
    }

    @Override
    protected void masterOperation(final ClusterAllocationExplainRequest request, final ClusterState state,
                                   final ActionListener<ClusterAllocationExplainResponse> listener) {
        final RoutingNodes routingNodes = state.getRoutingNodes();
        final ClusterInfo clusterInfo = clusterInfoService.getClusterInfo();
        final RoutingAllocation allocation = new RoutingAllocation(allocationDeciders, routingNodes, state,
                clusterInfo, snapshotsInfoService.snapshotShardSizes(), System.nanoTime());

        ShardRouting shardRouting = findShardToExplain(request, allocation);
        logger.debug("explaining the allocation for [{}], found shard [{}]", request, shardRouting);

        ClusterAllocationExplanation cae = explainShard(shardRouting, allocation,
            request.includeDiskInfo() ? clusterInfo : null, request.includeYesDecisions(), allocationService);
        listener.onResponse(new ClusterAllocationExplainResponse(cae));
    }

    // public for testing
    public static ClusterAllocationExplanation explainShard(ShardRouting shardRouting, RoutingAllocation allocation,
                                                            ClusterInfo clusterInfo, boolean includeYesDecisions,
                                                            AllocationService allocationService) {
        allocation.setDebugMode(includeYesDecisions ? DebugMode.ON : DebugMode.EXCLUDE_YES_DECISIONS);

        ShardAllocationDecision shardDecision;
        if (shardRouting.initializing() || shardRouting.relocating()) {
            shardDecision = ShardAllocationDecision.NOT_TAKEN;
        } else {
            shardDecision = allocationService.explainShardAllocation(shardRouting, allocation);
        }

        return new ClusterAllocationExplanation(shardRouting,
            shardRouting.currentNodeId() != null ? allocation.nodes().get(shardRouting.currentNodeId()) : null,
            shardRouting.relocatingNodeId() != null ? allocation.nodes().get(shardRouting.relocatingNodeId()) : null,
            clusterInfo, shardDecision);
    }

    // public for testing
    public static ShardRouting findShardToExplain(ClusterAllocationExplainRequest request, RoutingAllocation allocation) {
        ShardRouting foundShard = null;
        if (request.useAnyUnassignedShard()) {
            // If we can use any shard, just pick the first unassigned one (if there are any)
            RoutingNodes.UnassignedShards.UnassignedIterator ui = allocation.routingNodes().unassigned().iterator();
            if (ui.hasNext()) {
                foundShard = ui.next();
            }
            if (foundShard == null) {
                throw new IllegalArgumentException("unable to find any unassigned shards to explain [" + request + "]");
            }
        } else {
            String index = request.getIndex();
            int shard = request.getShard();
            if (request.isPrimary()) {
                // If we're looking for the primary shard, there's only one copy, so pick it directly
                foundShard = allocation.routingTable().shardRoutingTable(index, shard).primaryShard();
                if (request.getCurrentNode() != null) {
                    DiscoveryNode primaryNode = allocation.nodes().resolveNode(request.getCurrentNode());
                    // the primary is assigned to a node other than the node specified in the request
                    if (primaryNode.getId().equals(foundShard.currentNodeId()) == false) {
                        throw new IllegalArgumentException(
                                "unable to find primary shard assigned to node [" + request.getCurrentNode() + "]");
                    }
                }
            } else {
                // If looking for a replica, go through all the replica shards
                List<ShardRouting> replicaShardRoutings = allocation.routingTable().shardRoutingTable(index, shard).replicaShards();
                if (request.getCurrentNode() != null) {
                    // the request is to explain a replica shard already assigned on a particular node,
                    // so find that shard copy
                    DiscoveryNode replicaNode = allocation.nodes().resolveNode(request.getCurrentNode());
                    for (ShardRouting replica : replicaShardRoutings) {
                        if (replicaNode.getId().equals(replica.currentNodeId())) {
                            foundShard = replica;
                            break;
                        }
                    }
                    if (foundShard == null) {
                        throw new IllegalArgumentException("unable to find a replica shard assigned to node [" +
                                                            request.getCurrentNode() + "]");
                    }
                } else {
                    if (replicaShardRoutings.size() > 0) {
                        // Pick the first replica at the very least
                        foundShard = replicaShardRoutings.get(0);
                        for (ShardRouting replica : replicaShardRoutings) {
                            // In case there are multiple replicas where some are assigned and some aren't,
                            // try to find one that is unassigned at least
                            if (replica.unassigned()) {
                                foundShard = replica;
                                break;
                            } else if (replica.started() && (foundShard.initializing() || foundShard.relocating())) {
                                // prefer started shards to initializing or relocating shards because started shards
                                // can be explained
                                foundShard = replica;
                            }
                        }
                    }
                }
            }
        }

        if (foundShard == null) {
            throw new IllegalArgumentException("unable to find any shards to explain [" + request + "] in the routing table");
        }
        return foundShard;
    }
}