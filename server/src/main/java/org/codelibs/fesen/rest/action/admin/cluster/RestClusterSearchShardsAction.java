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

package org.codelibs.fesen.rest.action.admin.cluster;

import java.io.IOException;
import java.util.List;

import org.codelibs.fesen.action.admin.cluster.shards.ClusterSearchShardsRequest;
import org.codelibs.fesen.action.support.IndicesOptions;
import org.codelibs.fesen.client.Requests;
import org.codelibs.fesen.client.node.NodeClient;
import org.codelibs.fesen.common.Strings;
import org.codelibs.fesen.rest.BaseRestHandler;
import org.codelibs.fesen.rest.RestRequest;
import org.codelibs.fesen.rest.action.RestToXContentListener;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.codelibs.fesen.rest.RestRequest.Method.GET;
import static org.codelibs.fesen.rest.RestRequest.Method.POST;

public class RestClusterSearchShardsAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(GET, "/_search_shards"),
            new Route(POST, "/_search_shards"),
            new Route(GET, "/{index}/_search_shards"),
            new Route(POST, "/{index}/_search_shards")));
    }

    @Override
    public String getName() {
        return "cluster_search_shards_action";
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        String[] indices = Strings.splitStringByCommaToArray(request.param("index"));
        final ClusterSearchShardsRequest clusterSearchShardsRequest = Requests.clusterSearchShardsRequest(indices);
        clusterSearchShardsRequest.local(request.paramAsBoolean("local", clusterSearchShardsRequest.local()));
        clusterSearchShardsRequest.routing(request.param("routing"));
        clusterSearchShardsRequest.preference(request.param("preference"));
        clusterSearchShardsRequest.indicesOptions(IndicesOptions.fromRequest(request, clusterSearchShardsRequest.indicesOptions()));
        return channel -> client.admin().cluster().searchShards(clusterSearchShardsRequest, new RestToXContentListener<>(channel));
    }
}
