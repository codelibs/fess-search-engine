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

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.codelibs.fesen.index.query.QueryShardContext;
import org.codelibs.fesen.search.aggregations.Aggregator;
import org.codelibs.fesen.search.aggregations.AggregatorFactories;
import org.codelibs.fesen.search.aggregations.AggregatorFactory;
import org.codelibs.fesen.search.aggregations.CardinalityUpperBound;
import org.codelibs.fesen.search.fetch.StoredFieldsContext;
import org.codelibs.fesen.search.fetch.subphase.FetchDocValuesContext;
import org.codelibs.fesen.search.fetch.subphase.FetchFieldsContext;
import org.codelibs.fesen.search.fetch.subphase.FetchSourceContext;
import org.codelibs.fesen.search.fetch.subphase.FieldAndFormat;
import org.codelibs.fesen.search.fetch.subphase.ScriptFieldsContext;
import org.codelibs.fesen.search.fetch.subphase.highlight.HighlightBuilder;
import org.codelibs.fesen.search.internal.SearchContext;
import org.codelibs.fesen.search.internal.SubSearchContext;
import org.codelibs.fesen.search.sort.SortAndFormats;

class TopHitsAggregatorFactory extends AggregatorFactory {

    private final int from;
    private final int size;
    private final boolean explain;
    private final boolean version;
    private final boolean seqNoAndPrimaryTerm;
    private final boolean trackScores;
    private final Optional<SortAndFormats> sort;
    private final HighlightBuilder highlightBuilder;
    private final StoredFieldsContext storedFieldsContext;
    private final List<FieldAndFormat> docValueFields;
    private final List<FieldAndFormat> fetchFields;
    private final List<ScriptFieldsContext.ScriptField> scriptFields;
    private final FetchSourceContext fetchSourceContext;

    TopHitsAggregatorFactory(String name,
                                int from,
                                int size,
                                boolean explain,
                                boolean version,
                                boolean seqNoAndPrimaryTerm,
                                boolean trackScores,
                                Optional<SortAndFormats> sort,
                                HighlightBuilder highlightBuilder,
                                StoredFieldsContext storedFieldsContext,
                                List<FieldAndFormat> docValueFields,
                                List<FieldAndFormat> fetchFields,
                                List<ScriptFieldsContext.ScriptField> scriptFields,
                                FetchSourceContext fetchSourceContext,
                                QueryShardContext queryShardContext,
                                AggregatorFactory parent,
                                AggregatorFactories.Builder subFactories,
                                Map<String, Object> metadata) throws IOException {
        super(name, queryShardContext, parent, subFactories, metadata);
        this.from = from;
        this.size = size;
        this.explain = explain;
        this.version = version;
        this.seqNoAndPrimaryTerm = seqNoAndPrimaryTerm;
        this.trackScores = trackScores;
        this.sort = sort;
        this.highlightBuilder = highlightBuilder;
        this.storedFieldsContext = storedFieldsContext;
        this.docValueFields = docValueFields;
        this.fetchFields = fetchFields;
        this.scriptFields = scriptFields;
        this.fetchSourceContext = fetchSourceContext;
    }

    @Override
    public Aggregator createInternal(SearchContext searchContext,
                                        Aggregator parent,
                                        CardinalityUpperBound cardinality,
                                        Map<String, Object> metadata) throws IOException {
        SubSearchContext subSearchContext = new SubSearchContext(searchContext);
        subSearchContext.parsedQuery(searchContext.parsedQuery());
        subSearchContext.explain(explain);
        subSearchContext.version(version);
        subSearchContext.seqNoAndPrimaryTerm(seqNoAndPrimaryTerm);
        subSearchContext.trackScores(trackScores);
        subSearchContext.from(from);
        subSearchContext.size(size);
        if (sort.isPresent()) {
            subSearchContext.sort(sort.get());
        }
        if (storedFieldsContext != null) {
            subSearchContext.storedFieldsContext(storedFieldsContext);
        }
        if (docValueFields != null) {
            FetchDocValuesContext docValuesContext = FetchDocValuesContext.create(searchContext.mapperService(), docValueFields);
            subSearchContext.docValuesContext(docValuesContext);
        }
        if (fetchFields != null) {
            FetchFieldsContext fieldsContext = new FetchFieldsContext(fetchFields);
            subSearchContext.fetchFieldsContext(fieldsContext);
        }
        for (ScriptFieldsContext.ScriptField field : scriptFields) {
            subSearchContext.scriptFields().add(field);
            }
        if (fetchSourceContext != null) {
            subSearchContext.fetchSourceContext(fetchSourceContext);
        }
        if (highlightBuilder != null) {
            subSearchContext.highlight(highlightBuilder.build(searchContext.getQueryShardContext()));
        }
        return new TopHitsAggregator(searchContext.fetchPhase(), subSearchContext, name, searchContext, parent, metadata);
    }

}