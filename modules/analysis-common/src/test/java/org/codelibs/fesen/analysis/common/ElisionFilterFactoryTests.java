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

package org.codelibs.fesen.analysis.common;

import org.codelibs.fesen.analysis.common.CommonAnalysisPlugin;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.env.Environment;
import org.codelibs.fesen.index.analysis.AnalysisTestsHelper;
import org.codelibs.fesen.test.ESTokenStreamTestCase;

import java.io.IOException;

public class ElisionFilterFactoryTests extends ESTokenStreamTestCase {

    public void testElisionFilterWithNoArticles() throws IOException {
        Settings settings = Settings.builder()
            .put("index.analysis.filter.elision.type", "elision")
            .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir().toString())
            .build();

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class,
            () -> AnalysisTestsHelper.createTestAnalysisFromSettings(settings, new CommonAnalysisPlugin()));

        assertEquals("elision filter requires [articles] or [articles_path] setting", e.getMessage());
    }

}
