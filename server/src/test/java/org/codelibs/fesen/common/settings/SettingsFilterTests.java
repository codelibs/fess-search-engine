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
package org.codelibs.fesen.common.settings;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.codelibs.fesen.common.Strings;
import org.codelibs.fesen.common.logging.Loggers;
import org.codelibs.fesen.common.settings.Setting;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.common.settings.SettingsFilter;
import org.codelibs.fesen.common.settings.Setting.Property;
import org.codelibs.fesen.common.xcontent.XContentBuilder;
import org.codelibs.fesen.common.xcontent.json.JsonXContent;
import org.codelibs.fesen.rest.RestRequest;
import org.codelibs.fesen.test.ESTestCase;
import org.codelibs.fesen.test.MockLogAppender;
import org.codelibs.fesen.test.rest.FakeRestRequest;
import org.apache.logging.log4j.LogManager;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Consumer;

import static org.hamcrest.CoreMatchers.equalTo;

public class SettingsFilterTests extends ESTestCase {
    public void testAddingAndRemovingFilters() {
        HashSet<String> hashSet = new HashSet<>(Arrays.asList("foo", "bar", "baz"));
        SettingsFilter settingsFilter = new SettingsFilter(hashSet);
        assertEquals(settingsFilter.getPatterns(), hashSet);
    }

    public void testSettingsFiltering() throws IOException {

        testFiltering(Settings.builder()
                        .put("foo", "foo_test")
                        .put("foo1", "foo1_test")
                        .put("bar", "bar_test")
                        .put("bar1", "bar1_test")
                        .put("bar.2", "bar2_test")
                        .build(),
                    Settings.builder()
                        .put("foo1", "foo1_test")
                        .build(),
                "foo", "bar*"
        );

        testFiltering(Settings.builder()
                        .put("foo", "foo_test")
                        .put("foo1", "foo1_test")
                        .put("bar", "bar_test")
                        .put("bar1", "bar1_test")
                        .put("bar.2", "bar2_test")
                        .build(),
                Settings.builder()
                        .put("foo", "foo_test")
                        .put("foo1", "foo1_test")
                        .build(),
                "bar*"
        );

        testFiltering(Settings.builder()
                        .put("foo", "foo_test")
                        .put("foo1", "foo1_test")
                        .put("bar", "bar_test")
                        .put("bar1", "bar1_test")
                        .put("bar.2", "bar2_test")
                        .build(),
                Settings.builder()
                        .build(),
                "foo", "bar*", "foo*"
        );

        testFiltering(Settings.builder()
                        .put("foo", "foo_test")
                        .put("bar", "bar_test")
                        .put("baz", "baz_test")
                        .build(),
                Settings.builder()
                        .put("foo", "foo_test")
                        .put("bar", "bar_test")
                        .put("baz", "baz_test")
                        .build()
        );

        testFiltering(Settings.builder()
                .put("a.b.something.d", "foo_test")
                .put("a.b.something.c", "foo1_test")
                .build(),
            Settings.builder()
                .put("a.b.something.c", "foo1_test")
                .build(),
            "a.b.*.d"
        );
    }

    public void testFilteredSettingIsNotLogged() throws Exception {
        Settings oldSettings = Settings.builder().put("key", "old").build();
        Settings newSettings = Settings.builder().put("key", "new").build();

        Setting<String> filteredSetting = Setting.simpleString("key", Property.Filtered);
        assertExpectedLogMessages((testLogger) -> Setting.logSettingUpdate(filteredSetting, newSettings, oldSettings, testLogger),
            new MockLogAppender.SeenEventExpectation("secure logging", "org.codelibs.fesen.test", Level.INFO, "updating [key]"),
            new MockLogAppender.UnseenEventExpectation("unwanted old setting name", "org.codelibs.fesen.test", Level.INFO, "*old*"),
            new MockLogAppender.UnseenEventExpectation("unwanted new setting name", "org.codelibs.fesen.test", Level.INFO, "*new*")
        );
    }

    public void testRegularSettingUpdateIsFullyLogged() throws Exception {
        Settings oldSettings = Settings.builder().put("key", "old").build();
        Settings newSettings = Settings.builder().put("key", "new").build();

        Setting<String> regularSetting = Setting.simpleString("key");
        assertExpectedLogMessages((testLogger) -> Setting.logSettingUpdate(regularSetting, newSettings, oldSettings, testLogger),
            new MockLogAppender.SeenEventExpectation("regular logging", "org.codelibs.fesen.test", Level.INFO,
            "updating [key] from [old] to [new]"));
    }

    private void assertExpectedLogMessages(Consumer<Logger> consumer,
                                           MockLogAppender.LoggingExpectation ... expectations) throws IllegalAccessException {
        Logger testLogger = LogManager.getLogger("org.codelibs.fesen.test");
        MockLogAppender appender = new MockLogAppender();
        Loggers.addAppender(testLogger, appender);
        try {
            appender.start();
            Arrays.stream(expectations).forEach(appender::addExpectation);
            consumer.accept(testLogger);
            appender.assertAllExpectationsMatched();
        } finally {
            Loggers.removeAppender(testLogger, appender);
        }
    }

    private void testFiltering(Settings source, Settings filtered, String... patterns) throws IOException {
        SettingsFilter settingsFilter = new SettingsFilter(Arrays.asList(patterns));

        // Test using direct filtering
        Settings filteredSettings = settingsFilter.filter(source);
        assertThat(filteredSettings, equalTo(filtered));

        // Test using toXContent filtering
        RestRequest request = new FakeRestRequest();
        settingsFilter.addFilterSettingParams(request);
        XContentBuilder xContentBuilder = XContentBuilder.builder(JsonXContent.jsonXContent);
        xContentBuilder.startObject();
        source.toXContent(xContentBuilder, request);
        xContentBuilder.endObject();
        String filteredSettingsString = Strings.toString(xContentBuilder);
        filteredSettings = Settings.builder().loadFromSource(filteredSettingsString, xContentBuilder.contentType()).build();
        assertThat(filteredSettings, equalTo(filtered));
    }
}
