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

package org.codelibs.fesen.rest.action.admin.indices;

import org.codelibs.fesen.action.admin.indices.shards.IndicesShardStoresAction;
import org.codelibs.fesen.action.admin.indices.shards.IndicesShardStoresRequest;
import org.codelibs.fesen.action.admin.indices.shards.IndicesShardStoresResponse;
import org.codelibs.fesen.action.support.IndicesOptions;
import org.codelibs.fesen.client.node.NodeClient;
import org.codelibs.fesen.common.Strings;
import org.codelibs.fesen.common.xcontent.XContentBuilder;
import org.codelibs.fesen.rest.BaseRestHandler;
import org.codelibs.fesen.rest.BytesRestResponse;
import org.codelibs.fesen.rest.RestRequest;
import org.codelibs.fesen.rest.RestResponse;
import org.codelibs.fesen.rest.action.RestBuilderListener;

import java.io.IOException;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static org.codelibs.fesen.rest.RestRequest.Method.GET;
import static org.codelibs.fesen.rest.RestStatus.OK;

/**
 * Rest action for {@link IndicesShardStoresAction}
 */
public class RestIndicesShardStoresAction extends BaseRestHandler {

    @Override
    public List<Route> routes() {
        return unmodifiableList(asList(
            new Route(GET, "/_shard_stores"),
            new Route(GET, "/{index}/_shard_stores")));
    }

    @Override
    public String getName() {
        return "indices_shard_stores_action";
    }

    @Override
    public boolean allowSystemIndexAccessByDefault() {
        return true;
    }

    @Override
    public RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client) throws IOException {
        IndicesShardStoresRequest indicesShardStoresRequest = new IndicesShardStoresRequest(
                Strings.splitStringByCommaToArray(request.param("index")));
        if (request.hasParam("status")) {
            indicesShardStoresRequest.shardStatuses(Strings.splitStringByCommaToArray(request.param("status")));
        }
        indicesShardStoresRequest.indicesOptions(IndicesOptions.fromRequest(request, indicesShardStoresRequest.indicesOptions()));
        return channel ->
            client.admin()
                .indices()
                .shardStores(indicesShardStoresRequest, new RestBuilderListener<IndicesShardStoresResponse>(channel) {
                    @Override
                    public RestResponse buildResponse(
                        IndicesShardStoresResponse response,
                        XContentBuilder builder) throws Exception {
                        builder.startObject();
                        response.toXContent(builder, request);
                        builder.endObject();
                        return new BytesRestResponse(OK, builder);
                    }
                });
    }
}
