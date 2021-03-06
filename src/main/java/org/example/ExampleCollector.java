/*
 * Copyright 2015-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.example;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import org.glowroot.agent.shaded.com.google.common.collect.ImmutableList;
import org.glowroot.agent.shaded.com.google.common.collect.Lists;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValueMessage.GaugeValue;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.CollectorServiceOuterClass.InitMessage.Environment;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.CollectorServiceOuterClass.LogMessage.LogEvent;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.TraceOuterClass.Trace;
import org.glowroot.agent.shaded.org.slf4j.Logger;
import org.glowroot.agent.shaded.org.slf4j.LoggerFactory;

public class ExampleCollector implements org.glowroot.agent.collector.Collector {

    private static final Logger logger = LoggerFactory.getLogger(ExampleCollector.class);

    private static final JsonFactory jsonFactory = new JsonFactory();

    @Override
    public void init(List<File> confDirs, Environment environment, AgentConfig agentConfig,
            AgentConfigUpdater agentConfigUpdater) {}

    @Override
    public void collectAggregates(AggregateReader aggregateReader) throws Exception {
        CollectingAggregateVisitor aggregateVisitor = new CollectingAggregateVisitor();
        aggregateReader.accept(aggregateVisitor);

        for (CollectedAggregate collectedAggregate : aggregateVisitor.collectedAggregates) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            JsonGenerator jg = jsonFactory.createGenerator(baos);
            new AggregateWriter(jg).write(collectedAggregate.transactionType,
                    collectedAggregate.aggregate, collectedAggregate.sharedQueryTexts);
            jg.close();
            logger.info(baos.toString());
        }
    }

    @Override
    public void collectGaugeValues(List<GaugeValue> gaugeValues) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator jg = jsonFactory.createGenerator(baos);
        jg.writeStartArray();
        for (GaugeValue gaugeValue : gaugeValues) {
            jg.writeStartObject();
            jg.writeStringField("gaugeName", gaugeValue.getGaugeName());
            jg.writeNumberField("captureTime", gaugeValue.getCaptureTime());
            jg.writeNumberField("value", gaugeValue.getValue());
            jg.writeNumberField("weight", gaugeValue.getWeight());
            jg.writeEndObject();
        }
        jg.writeEndArray();
        jg.close();
        logger.info(baos.toString());
    }

    @Override
    public void collectTrace(TraceReader traceReader) throws Exception {
        CollectingTraceVisitor traceVisitor = new CollectingTraceVisitor();
        traceReader.accept(traceVisitor);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        JsonGenerator jg = jsonFactory.createGenerator(baos);
        new TraceWriter(jg).write(traceVisitor.header, traceVisitor.entries, traceVisitor.queries,
                traceVisitor.sharedQueryTexts, traceVisitor.mainThreadProfile,
                traceVisitor.auxThreadProfile);
        jg.close();
        logger.info(baos.toString());
    }

    @Override
    public void log(LogEvent logEvent) {}

    private static class CollectingAggregateVisitor implements AggregateVisitor {

        private final List<CollectedAggregate> collectedAggregates =
                new ArrayList<CollectedAggregate>();

        @Override
        public void visitOverallAggregate(String transactionType,
                List<String> sharedQueryTexts, Aggregate overallAggregate) throws Exception {
            collectedAggregates.add(
                    new CollectedAggregate(transactionType, overallAggregate, sharedQueryTexts));
        }

        @Override
        public void visitTransactionAggregate(String transactionType, String transactionName,
                List<String> sharedQueryTexts, Aggregate transactionAggregate) throws Exception {
            // this is the breakdown per transaction name (the transaction sidebar in Glowroot UI)
            // ignoring to keep the example collector simple
        }
    }

    private static class CollectedAggregate {

        private final String transactionType;
        private final Aggregate aggregate;
        private final List<String> sharedQueryTexts;

        private CollectedAggregate(String transactionType, Aggregate aggregate,
                List<String> sharedQueryTexts) {
            this.transactionType = transactionType;
            this.aggregate = aggregate;
            this.sharedQueryTexts = sharedQueryTexts;
        }
    }

    private static class CollectingTraceVisitor implements TraceVisitor {

        private final List<Trace.Entry> entries = Lists.newArrayList();
        private List<Aggregate.Query> queries = ImmutableList.of();
        private List<String> sharedQueryTexts = ImmutableList.of();
        private Profile mainThreadProfile;
        private Profile auxThreadProfile;
        private Trace.Header header;

        @Override
        public void visitEntry(Trace.Entry entry) {
            entries.add(entry);
        }

        @Override
        public void visitQueries(List<Aggregate.Query> queries) {
            this.queries = queries;
        }

        @Override
        public void visitSharedQueryTexts(List<String> sharedQueryTexts) {
            this.sharedQueryTexts = sharedQueryTexts;
        }

        @Override
        public void visitMainThreadProfile(Profile profile) {
            mainThreadProfile = profile;
        }

        @Override
        public void visitAuxThreadProfile(Profile profile) {
            auxThreadProfile = profile;
        }

        @Override
        public void visitHeader(Trace.Header header) {
            this.header = header;
        }
    }
}
