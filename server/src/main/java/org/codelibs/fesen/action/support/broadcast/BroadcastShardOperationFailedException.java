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

package org.codelibs.fesen.action.support.broadcast;

import java.io.IOException;

import org.codelibs.fesen.ElasticsearchException;
import org.codelibs.fesen.ElasticsearchWrapperException;
import org.codelibs.fesen.common.io.stream.StreamInput;
import org.codelibs.fesen.index.shard.ShardId;

/**
 * An exception indicating that a failure occurred performing an operation on the shard.
 *
 *
 */
public class BroadcastShardOperationFailedException extends ElasticsearchException implements ElasticsearchWrapperException {

    public BroadcastShardOperationFailedException(ShardId shardId, String msg) {
        this(shardId, msg, null);
    }

    public BroadcastShardOperationFailedException(ShardId shardId, Throwable cause) {
        this(shardId, "", cause);
    }

    public BroadcastShardOperationFailedException(ShardId shardId, String msg, Throwable cause) {
        super(msg, cause);
        setShard(shardId);
    }

    public BroadcastShardOperationFailedException(StreamInput in) throws IOException{
        super(in);
    }
}