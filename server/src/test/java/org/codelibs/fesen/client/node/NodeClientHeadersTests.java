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

package org.codelibs.fesen.client.node;

import org.codelibs.fesen.action.ActionListener;
import org.codelibs.fesen.action.ActionRequest;
import org.codelibs.fesen.action.ActionType;
import org.codelibs.fesen.action.support.ActionFilters;
import org.codelibs.fesen.action.support.TransportAction;
import org.codelibs.fesen.client.AbstractClientHeadersTestCase;
import org.codelibs.fesen.client.Client;
import org.codelibs.fesen.client.node.NodeClient;
import org.codelibs.fesen.common.io.stream.NamedWriteableRegistry;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.tasks.Task;
import org.codelibs.fesen.tasks.TaskManager;
import org.codelibs.fesen.threadpool.ThreadPool;

import java.util.Collections;
import java.util.HashMap;

public class NodeClientHeadersTests extends AbstractClientHeadersTestCase {

    private static final ActionFilters EMPTY_FILTERS = new ActionFilters(Collections.emptySet());

    @Override
    protected Client buildClient(Settings headersSettings, ActionType[] testedActions) {
        Settings settings = HEADER_SETTINGS;
        Actions actions = new Actions(settings, threadPool, testedActions);
        NodeClient client = new NodeClient(settings, threadPool);
        client.initialize(actions, () -> "test", null,
            new NamedWriteableRegistry(Collections.emptyList()));
        return client;
    }

    private static class Actions extends HashMap<ActionType, TransportAction> {

        private Actions(Settings settings, ThreadPool threadPool, ActionType[] actions) {
            for (ActionType action : actions) {
                put(action, new InternalTransportAction(settings, action.name(), threadPool));
            }
        }
    }

    private static class InternalTransportAction extends TransportAction {

        private InternalTransportAction(Settings settings, String actionName, ThreadPool threadPool) {
            super(actionName, EMPTY_FILTERS, new TaskManager(settings, threadPool, Collections.emptySet()));
        }

        @Override
        protected void doExecute(Task task, ActionRequest request, ActionListener listener) {
            listener.onFailure(new InternalException(actionName));
        }
    }


}
