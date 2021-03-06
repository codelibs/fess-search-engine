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

package org.codelibs.fesen.action.admin.cluster.node.liveness;

import org.codelibs.fesen.cluster.service.ClusterService;
import org.codelibs.fesen.common.inject.Inject;
import org.codelibs.fesen.tasks.Task;
import org.codelibs.fesen.threadpool.ThreadPool;
import org.codelibs.fesen.transport.TransportChannel;
import org.codelibs.fesen.transport.TransportRequestHandler;
import org.codelibs.fesen.transport.TransportService;

public final class TransportLivenessAction implements TransportRequestHandler<LivenessRequest> {

    private final ClusterService clusterService;
    public static final String NAME = "cluster:monitor/nodes/liveness";

    @Inject
    public TransportLivenessAction(ClusterService clusterService, TransportService transportService) {
        this.clusterService = clusterService;
        transportService.registerRequestHandler(NAME, ThreadPool.Names.SAME,
            false, false /*can not trip circuit breaker*/, LivenessRequest::new, this);
    }

    @Override
    public void messageReceived(LivenessRequest request, TransportChannel channel, Task task) throws Exception {
        channel.sendResponse(new LivenessResponse(clusterService.getClusterName(), clusterService.localNode()));
    }
}
