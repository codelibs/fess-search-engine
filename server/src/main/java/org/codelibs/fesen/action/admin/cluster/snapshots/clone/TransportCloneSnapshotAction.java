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

package org.codelibs.fesen.action.admin.cluster.snapshots.clone;

import java.io.IOException;

import org.codelibs.fesen.action.ActionListener;
import org.codelibs.fesen.action.support.ActionFilters;
import org.codelibs.fesen.action.support.master.AcknowledgedResponse;
import org.codelibs.fesen.action.support.master.TransportMasterNodeAction;
import org.codelibs.fesen.cluster.ClusterState;
import org.codelibs.fesen.cluster.block.ClusterBlockException;
import org.codelibs.fesen.cluster.block.ClusterBlockLevel;
import org.codelibs.fesen.cluster.metadata.IndexNameExpressionResolver;
import org.codelibs.fesen.cluster.service.ClusterService;
import org.codelibs.fesen.common.inject.Inject;
import org.codelibs.fesen.common.io.stream.StreamInput;
import org.codelibs.fesen.snapshots.SnapshotsService;
import org.codelibs.fesen.threadpool.ThreadPool;
import org.codelibs.fesen.transport.TransportService;

/**
 * Transport action for the clone snapshot operation.
 */
public final class TransportCloneSnapshotAction extends TransportMasterNodeAction<CloneSnapshotRequest, AcknowledgedResponse> {

    private final SnapshotsService snapshotsService;

    @Inject
    public TransportCloneSnapshotAction(TransportService transportService, ClusterService clusterService,
                                        ThreadPool threadPool, SnapshotsService snapshotsService, ActionFilters actionFilters,
                                        IndexNameExpressionResolver indexNameExpressionResolver) {
        super(CloneSnapshotAction.NAME, transportService, clusterService, threadPool, actionFilters, CloneSnapshotRequest::new,
                indexNameExpressionResolver);
        this.snapshotsService = snapshotsService;
    }

    @Override
    protected String executor() {
        return ThreadPool.Names.SAME;
    }

    @Override
    protected AcknowledgedResponse read(StreamInput in) throws IOException {
        return new AcknowledgedResponse(in);
    }

    @Override
    protected void masterOperation(CloneSnapshotRequest request, ClusterState state, ActionListener<AcknowledgedResponse> listener) {
        snapshotsService.cloneSnapshot(request, ActionListener.map(listener, v -> new AcknowledgedResponse(true)));
    }

    @Override
    protected ClusterBlockException checkBlock(CloneSnapshotRequest request, ClusterState state) {
        // Cluster is not affected but we look up repositories in metadata
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_READ);
    }
}
