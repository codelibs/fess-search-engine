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

package org.codelibs.fesen.search.aggregations.pipeline;

import org.codelibs.fesen.search.DocValueFormat;
import org.codelibs.fesen.search.aggregations.ParsedAggregation;
import org.codelibs.fesen.search.aggregations.metrics.InternalExtendedStats;
import org.codelibs.fesen.search.aggregations.metrics.InternalExtendedStatsTests;
import org.codelibs.fesen.search.aggregations.pipeline.InternalExtendedStatsBucket;
import org.codelibs.fesen.search.aggregations.pipeline.ParsedExtendedStatsBucket;

import java.util.List;
import java.util.Map;

public class InternalExtendedStatsBucketTests extends InternalExtendedStatsTests {

    @Override
    protected InternalExtendedStatsBucket createInstance(String name, long count, double sum, double min,
                                                         double max, double sumOfSqrs,
                                                         double sigma, DocValueFormat formatter,
                                                         Map<String, Object> metadata) {
        return new InternalExtendedStatsBucket(name, count, sum, min, max, sumOfSqrs, sigma, formatter, metadata);
    }

    @Override
    public void testReduceRandom() {
        expectThrows(UnsupportedOperationException.class, () -> createTestInstance("name", null).reduce(null, null));
    }

    @Override
    protected void assertReduced(InternalExtendedStats reduced, List<InternalExtendedStats> inputs) {
        // no test since reduce operation is unsupported
    }

    @Override
    protected void assertFromXContent(InternalExtendedStats aggregation, ParsedAggregation parsedAggregation) {
        super.assertFromXContent(aggregation, parsedAggregation);
        assertTrue(parsedAggregation instanceof ParsedExtendedStatsBucket);
    }
}