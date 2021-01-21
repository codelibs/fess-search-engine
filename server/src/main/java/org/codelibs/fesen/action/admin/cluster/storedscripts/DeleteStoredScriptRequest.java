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

package org.codelibs.fesen.action.admin.cluster.storedscripts;

import static org.codelibs.fesen.action.ValidateActions.addValidationError;

import java.io.IOException;

import org.codelibs.fesen.Version;
import org.codelibs.fesen.action.ActionRequestValidationException;
import org.codelibs.fesen.action.support.master.AcknowledgedRequest;
import org.codelibs.fesen.common.io.stream.StreamInput;
import org.codelibs.fesen.common.io.stream.StreamOutput;

public class DeleteStoredScriptRequest extends AcknowledgedRequest<DeleteStoredScriptRequest> {

    private String id;

    public DeleteStoredScriptRequest(StreamInput in) throws IOException {
        super(in);
        if (in.getVersion().before(Version.V_6_0_0_alpha2)) {
            in.readString(); // read lang from previous versions
        }

        id = in.readString();
    }

    DeleteStoredScriptRequest() {
        super();
    }

    public DeleteStoredScriptRequest(String id) {
        super();

        this.id = id;
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;

        if (id == null || id.isEmpty()) {
            validationException = addValidationError("must specify id for stored script", validationException);
        } else if (id.contains("#")) {
            validationException = addValidationError("id cannot contain '#' for stored script", validationException);
        }

        return validationException;
    }

    public String id() {
        return id;
    }

    public DeleteStoredScriptRequest id(String id) {
        this.id = id;

        return this;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);

        if (out.getVersion().before(Version.V_6_0_0_alpha2)) {
            out.writeString(""); // write an empty lang to previous versions
        }

        out.writeString(id);
    }

    @Override
    public String toString() {
        return "delete stored script {id [" + id + "]}";
    }
}