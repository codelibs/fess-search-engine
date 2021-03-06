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

package org.codelibs.fesen.rest.action.cat;

import org.codelibs.fesen.Version;
import org.codelibs.fesen.action.admin.cluster.node.info.NodesInfoResponse;
import org.codelibs.fesen.action.admin.cluster.node.stats.NodesStatsResponse;
import org.codelibs.fesen.action.admin.cluster.state.ClusterStateResponse;
import org.codelibs.fesen.client.node.NodeClient;
import org.codelibs.fesen.cluster.ClusterName;
import org.codelibs.fesen.cluster.ClusterState;
import org.codelibs.fesen.cluster.node.DiscoveryNode;
import org.codelibs.fesen.cluster.node.DiscoveryNodes;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.rest.action.cat.RestNodesAction;
import org.codelibs.fesen.test.ESTestCase;
import org.codelibs.fesen.test.rest.FakeRestRequest;
import org.codelibs.fesen.threadpool.TestThreadPool;
import org.junit.Before;

import java.util.Collections;

import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestNodesActionTests extends ESTestCase {

    private RestNodesAction action;

    @Before
    public void setUpAction() {
        action = new RestNodesAction();
    }

    public void testBuildTableDoesNotThrowGivenNullNodeInfoAndStats() {
        ClusterName clusterName = new ClusterName("cluster-1");
        DiscoveryNodes.Builder builder = DiscoveryNodes.builder();
        builder.add(new DiscoveryNode("node-1", buildNewFakeTransportAddress(), emptyMap(), emptySet(), Version.CURRENT));
        DiscoveryNodes discoveryNodes = builder.build();
        ClusterState clusterState = mock(ClusterState.class);
        when(clusterState.nodes()).thenReturn(discoveryNodes);

        ClusterStateResponse clusterStateResponse = new ClusterStateResponse(clusterName, clusterState, false);
        NodesInfoResponse nodesInfoResponse = new NodesInfoResponse(clusterName, Collections.emptyList(), Collections.emptyList());
        NodesStatsResponse nodesStatsResponse = new NodesStatsResponse(clusterName, Collections.emptyList(), Collections.emptyList());

        action.buildTable(false, new FakeRestRequest(), clusterStateResponse, nodesInfoResponse, nodesStatsResponse);
    }

    public void testCatNodesWithLocalDeprecationWarning() {
        TestThreadPool threadPool = new TestThreadPool(RestNodesActionTests.class.getName());
        NodeClient client = new NodeClient(Settings.EMPTY, threadPool);
        FakeRestRequest request = new FakeRestRequest();
        request.params().put("local", randomFrom("", "true", "false"));

        action.doCatRequest(request, client);
        assertWarnings(RestNodesAction.LOCAL_DEPRECATED_MESSAGE);

        terminate(threadPool);
    }
}
