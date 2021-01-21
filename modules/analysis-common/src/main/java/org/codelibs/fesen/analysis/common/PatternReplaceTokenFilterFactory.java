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

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.pattern.PatternReplaceFilter;
import org.codelibs.fesen.common.regex.Regex;
import org.codelibs.fesen.common.settings.Settings;
import org.codelibs.fesen.env.Environment;
import org.codelibs.fesen.index.IndexSettings;
import org.codelibs.fesen.index.analysis.AbstractTokenFilterFactory;

import java.util.regex.Pattern;

public class PatternReplaceTokenFilterFactory extends AbstractTokenFilterFactory {

    private final Pattern pattern;
    private final String replacement;
    private final boolean all;

    public PatternReplaceTokenFilterFactory(IndexSettings indexSettings, Environment environment, String name, Settings settings) {
        super(indexSettings, name, settings);

        String sPattern = settings.get("pattern", null);
        if (sPattern == null) {
            throw new IllegalArgumentException("pattern is missing for [" + name + "] token filter of type 'pattern_replace'");
        }
        this.pattern = Regex.compile(sPattern, settings.get("flags"));
        this.replacement = settings.get("replacement", "");
        this.all = settings.getAsBoolean("all", true);
    }

    @Override
    public TokenStream create(TokenStream tokenStream) {
        return new PatternReplaceFilter(tokenStream, pattern, replacement, all);
    }
}