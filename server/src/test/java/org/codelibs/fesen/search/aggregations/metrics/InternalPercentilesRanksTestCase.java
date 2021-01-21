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

package org.codelibs.fesen.search.aggregations.metrics;

import static org.hamcrest.Matchers.equalTo;

import org.codelibs.fesen.search.aggregations.InternalAggregation;
import org.codelibs.fesen.search.aggregations.ParsedAggregation;
import org.codelibs.fesen.search.aggregations.metrics.ParsedPercentiles;
import org.codelibs.fesen.search.aggregations.metrics.Percentile;
import org.codelibs.fesen.search.aggregations.metrics.PercentileRanks;

public abstract class InternalPercentilesRanksTestCase<T extends InternalAggregation & PercentileRanks>
        extends AbstractPercentilesTestCase<T> {

    @Override
    protected final void assertFromXContent(T aggregation, ParsedAggregation parsedAggregation) {
        assertTrue(parsedAggregation instanceof PercentileRanks);
        PercentileRanks parsedPercentileRanks = (PercentileRanks) parsedAggregation;

        for (Percentile percentile : aggregation) {
            Double value = percentile.getValue();
            assertEquals(aggregation.percent(value), parsedPercentileRanks.percent(value), 0);
            assertEquals(aggregation.percentAsString(value), parsedPercentileRanks.percentAsString(value));
        }

        Class<? extends ParsedPercentiles> parsedClass = implementationClass();
        assertTrue(parsedClass != null && parsedClass.isInstance(parsedAggregation));
    }

    @Override
    protected void assertPercentile(T agg, Double value) {
        assertThat(agg.percent(value), equalTo(Double.NaN));
        assertThat(agg.percentAsString(value), equalTo("NaN"));
    }
}