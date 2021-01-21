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

import static org.codelibs.fesen.test.ESTestCase.createTestAnalysis;
import static org.hamcrest.Matchers.containsString;

import org.codelibs.fesen.Version;
import org.codelibs.fesen.analysis.common.CommonAnalysisPlugin;
import org.codelibs.fesen.analysis.common.PatternCaptureGroupTokenFilterFactory;
import org.codelibs.fesen.cluster.metadata.IndexMetadata;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.env.Environment;
import org.codelibs.fesen.index.IndexSettings;
import org.codelibs.fesen.index.analysis.IndexAnalyzers;
import org.codelibs.fesen.index.analysis.NamedAnalyzer;
import org.codelibs.fesen.test.ESTokenStreamTestCase;
import org.codelibs.fesen.test.IndexSettingsModule;

public class PatternCaptureTokenFilterTests extends ESTokenStreamTestCase {
    public void testPatternCaptureTokenFilter() throws Exception {
        String json = "/org/codelibs/fesen/analysis/common/pattern_capture.json";
        Settings settings = Settings.builder()
                .put(Environment.PATH_HOME_SETTING.getKey(), createTempDir())
                .loadFromStream(json, getClass().getResourceAsStream(json), false)
                .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
                .build();

        IndexSettings idxSettings = IndexSettingsModule.newIndexSettings("index", settings);
        IndexAnalyzers indexAnalyzers = createTestAnalysis(idxSettings, settings, new CommonAnalysisPlugin()).indexAnalyzers;
        NamedAnalyzer analyzer1 = indexAnalyzers.get("single");

        assertTokenStreamContents(analyzer1.tokenStream("test", "foobarbaz"), new String[]{"foobarbaz","foobar","foo"});

        NamedAnalyzer analyzer2 = indexAnalyzers.get("multi");

        assertTokenStreamContents(analyzer2.tokenStream("test", "abc123def"), new String[]{"abc123def","abc","123","def"});

        NamedAnalyzer analyzer3 = indexAnalyzers.get("preserve");

        assertTokenStreamContents(analyzer3.tokenStream("test", "foobarbaz"), new String[]{"foobar","foo"});
    }

    public void testNoPatterns() {
        try {
            new PatternCaptureGroupTokenFilterFactory(IndexSettingsModule.newIndexSettings("test", Settings.EMPTY), null,
                    "pattern_capture", Settings.builder().put("pattern", "foobar").build());
            fail ("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertThat(e.getMessage(), containsString("required setting 'patterns' is missing"));
        }
    }

}