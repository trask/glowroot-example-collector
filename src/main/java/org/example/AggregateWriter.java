/*
 * Copyright 2019 the original author or authors.
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

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonGenerator;

import org.glowroot.agent.shaded.org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.ProfileOuterClass.Profile;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.ProfileOuterClass.Profile.ProfileNode;

class AggregateWriter {

    private final JsonGenerator jg;

    AggregateWriter(JsonGenerator jg) {
        this.jg = jg;
    }

    void write(String transactionType, Aggregate aggregate, List<String> sharedQueryTexts)
            throws IOException {
        jg.writeStartObject();
        jg.writeStringField("transactionType", transactionType);
        jg.writeNumberField("totalDurationNanos", aggregate.getTotalDurationNanos());
        jg.writeNumberField("transactionCount", aggregate.getTransactionCount());
        jg.writeNumberField("errorCount", aggregate.getErrorCount());
        if (aggregate.getMainThreadRootTimerCount() > 0) {
            jg.writeArrayFieldStart("mainThreadFlattenedTimers");
            writeFlattenedTimers(aggregate.getMainThreadRootTimerList());
            jg.writeEndArray();
        }
        if (aggregate.hasAuxThreadRootTimer()) {
            jg.writeArrayFieldStart("auxThreadFlattenedTimers");
            writeFlattenedTimers(aggregate.getAuxThreadRootTimer());
            jg.writeEndArray();
        }
        if (aggregate.getAsyncTimerCount() > 0) {
            jg.writeArrayFieldStart("asyncTimers");
            writeAsyncTimers(aggregate.getAsyncTimerList());
            jg.writeEndArray();
        }
        if (aggregate.hasMainThreadStats()) {
            jg.writeFieldName("mainThreadStats");
            writeThreadStats(aggregate.getMainThreadStats());
        }
        if (aggregate.hasAuxThreadStats()) {
            jg.writeFieldName("auxThreadStats");
            writeThreadStats(aggregate.getAuxThreadStats());
        }
        if (aggregate.getQueryCount() > 0) {
            jg.writeFieldName("queries");
            writeQueries(aggregate.getQueryList(), sharedQueryTexts);
        }
        if (aggregate.hasMainThreadProfile()) {
            jg.writeFieldName("mainThreadProfile");
            writeProfile(aggregate.getMainThreadProfile());
        }
        if (aggregate.hasAuxThreadProfile()) {
            jg.writeFieldName("auxThreadProfile");
            writeProfile(aggregate.getAuxThreadProfile());
        }
        jg.writeEndObject();
    }

    private void writeQueries(List<Aggregate.Query> queries, List<String> sharedQueryTexts)
            throws IOException {
        jg.writeStartArray();
        for (Aggregate.Query query : queries) {
            writeQuery(query, sharedQueryTexts);
        }
        jg.writeEndArray();
    }

    private void writeProfile(Profile profile) throws IOException {
        jg.writeStartArray();
        // node ordering is pre-order depth-first
        // and there can be multiple "root" nodes (with depth=0)
        int priorDepth = -1;
        for (ProfileNode node : profile.getNodeList()) {
            int currDepth = node.getDepth();
            if (priorDepth != -1) {
                if (currDepth > priorDepth) {
                    jg.writeArrayFieldStart("childNodes");
                } else if (currDepth < priorDepth) {
                    for (int i = priorDepth; i > currDepth; i--) {
                        jg.writeEndObject();
                        jg.writeEndArray();
                    }
                    jg.writeEndObject();
                } else {
                    jg.writeEndObject();
                }
            }
            jg.writeStartObject();
            jg.writeStringField("stackTraceElement", getStackTraceElement(node, profile));
            Profile.LeafThreadState leafThreadState = node.getLeafThreadState();
            if (leafThreadState != Profile.LeafThreadState.NONE) {
                jg.writeStringField("leafThreadState", leafThreadState.name());
            }
            jg.writeNumberField("sampleCount", node.getSampleCount());
            priorDepth = currDepth;
        }
        if (priorDepth != -1) {
            jg.writeEndObject();
        }
        jg.writeEndArray();
    }

    private void writeQuery(Aggregate.Query query, List<String> sharedQueryTexts)
            throws IOException {
        jg.writeStartObject();
        jg.writeStringField("type", query.getType());
        jg.writeStringField("queryText", sharedQueryTexts.get(query.getSharedQueryTextIndex()));
        jg.writeNumberField("totalDurationNanos", query.getTotalDurationNanos());
        jg.writeNumberField("executionCount", query.getExecutionCount());
        if (query.hasTotalRows()) {
            jg.writeNumberField("totalRows", query.getTotalRows().getValue());
        }
        jg.writeBooleanField("active", query.getActive());
        jg.writeEndObject();
    }

    private String getStackTraceElement(ProfileNode node, Profile profile) {
        String className = profile.getClassName(node.getClassNameIndex());
        String methodName = profile.getMethodName(node.getMethodNameIndex());
        String fileName = profile.getFileName(node.getFileNameIndex());
        return new StackTraceElement(className, methodName, fileName, node.getLineNumber())
                .toString();
    }

    private void writeFlattenedTimers(List<Aggregate.Timer> rootTimers) throws IOException {
        Map<String, FlattenedTimer> flattenedTimers = new HashMap<String, FlattenedTimer>();
        for (Aggregate.Timer rootTimer : rootTimers) {
            flattenTimer(rootTimer, flattenedTimers, new HashSet<String>());
        }
        writeFlattenedTimers(flattenedTimers);
    }

    private void writeFlattenedTimers(Aggregate.Timer rootTimer) throws IOException {
        Map<String, FlattenedTimer> flattenedTimers = new HashMap<String, FlattenedTimer>();
        flattenTimer(rootTimer, flattenedTimers, new HashSet<String>());
        writeFlattenedTimers(flattenedTimers);
    }

    // need to keep track of parent timer names since the same timer can be nested underneath itself
    // when separated by another timer, e.g. abc > xyz > abc
    private void flattenTimer(Aggregate.Timer timer, Map<String, FlattenedTimer> flattenedTimers,
            Set<String> parentTimerNames) {
        FlattenedTimer flattenedTimer = flattenedTimers.get(timer.getName());
        if (flattenedTimer == null) {
            flattenedTimer = new FlattenedTimer();
            flattenedTimers.put(timer.getName(), flattenedTimer);
        }
        flattenedTimer.totalNanos += timer.getTotalNanos();
        flattenedTimer.count += timer.getCount();
        List<Aggregate.Timer> childTimers = timer.getChildTimerList();
        if (!childTimers.isEmpty()) {
            parentTimerNames.add(timer.getName());
            for (Aggregate.Timer childTimer : childTimers) {
                if (!parentTimerNames.contains(childTimer.getName())) {
                    flattenTimer(childTimer, flattenedTimers, parentTimerNames);
                }
            }
            parentTimerNames.remove(timer.getName());
        }
    }

    private void writeFlattenedTimers(Map<String, FlattenedTimer> flattenedTimers)
            throws IOException {
        for (Map.Entry<String, FlattenedTimer> entry : flattenedTimers.entrySet()) {
            FlattenedTimer flattenedTimer = entry.getValue();
            jg.writeStartObject();
            jg.writeStringField("name", entry.getKey());
            jg.writeNumberField("totalNanos", flattenedTimer.totalNanos);
            jg.writeNumberField("count", flattenedTimer.count);
            jg.writeEndObject();
        }
    }

    private void writeAsyncTimers(List<Aggregate.Timer> asyncTimers) throws IOException {
        for (Aggregate.Timer asyncTimer : asyncTimers) {
            jg.writeStartObject();
            jg.writeStringField("name", asyncTimer.getName());
            jg.writeNumberField("totalNanos", asyncTimer.getTotalNanos());
            jg.writeNumberField("count", asyncTimer.getCount());
            jg.writeEndObject();
        }
    }

    private void writeThreadStats(Aggregate.ThreadStats threadStats) throws IOException {
        jg.writeStartObject();
        jg.writeNumberField("totalCpuNanos", threadStats.getTotalCpuNanos());
        jg.writeNumberField("totalBlockedNanos", threadStats.getTotalBlockedNanos());
        jg.writeNumberField("totalWaitedNanos", threadStats.getTotalWaitedNanos());
        jg.writeNumberField("totalAllocatedBytes", threadStats.getTotalAllocatedBytes());
        jg.writeEndObject();
    }

    private static class FlattenedTimer {
        private long totalNanos;
        private long count;
    }
}
