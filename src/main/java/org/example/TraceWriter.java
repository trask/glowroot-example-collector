/*
 * Copyright 2018-2019 the original author or authors.
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
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.Proto;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.TraceOuterClass.Trace;

class TraceWriter {

    private final JsonGenerator jg;

    TraceWriter(JsonGenerator jg) {
        this.jg = jg;
    }

    static void writeHeader(JsonGenerator jg, Trace.Header header) throws IOException {
        new TraceWriter(jg).writeHeader(header);
    }

    static void writeEntries(JsonGenerator jg, List<Trace.Entry> entries,
            List<String> sharedQueryTexts) throws IOException {
        new TraceWriter(jg).writeEntries(entries, sharedQueryTexts);
    }

    static void writeQueries(JsonGenerator jg, List<Aggregate.Query> queries,
            List<String> sharedQueryTexts) throws IOException {
        new TraceWriter(jg).writeQueries(queries, sharedQueryTexts);
    }

    static void writeProfile(JsonGenerator jg, Profile profile) throws IOException {
        new TraceWriter(jg).writeProfile(profile);
    }

    private void writeHeader(Trace.Header header) throws IOException {
        jg.writeStartObject();
        if (header.getAsync()) {
            jg.writeBooleanField("async", true);
        }
        jg.writeNumberField("startTime", header.getStartTime());
        jg.writeNumberField("captureTime", header.getCaptureTime());
        jg.writeNumberField("durationNanos", header.getDurationNanos());
        jg.writeStringField("transactionType", header.getTransactionType());
        jg.writeStringField("transactionName", header.getTransactionName());
        jg.writeStringField("headline", header.getHeadline());
        jg.writeStringField("user", header.getUser());

        List<Trace.DetailEntry> detailEntries = header.getDetailEntryList();
        if (!detailEntries.isEmpty()) {
            jg.writeFieldName("detail");
            writeDetailEntries(detailEntries);
        }
        if (header.hasError()) {
            jg.writeFieldName("error");
            writeError(header.getError());
        }
        if (header.hasMainThreadRootTimer()) {
            jg.writeArrayFieldStart("mainThreadFlattenedTimers");
            writeFlattenedTimers(header.getMainThreadRootTimer());
            jg.writeEndArray();
        }
        if (header.hasAuxThreadRootTimer()) {
            jg.writeArrayFieldStart("auxThreadFlattenedTimers");
            writeFlattenedTimers(header.getAuxThreadRootTimer());
            jg.writeEndArray();
        }
        if (header.getAsyncTimerCount() > 0) {
            jg.writeArrayFieldStart("asyncTimers");
            writeAsyncTimers(header.getAsyncTimerList());
            jg.writeEndArray();
        }
        if (header.hasMainThreadRootTimer()) {
            jg.writeFieldName("mainThreadStats");
            writeThreadStats(header.getMainThreadStats());
        }
        if (header.hasAuxThreadRootTimer()) {
            jg.writeFieldName("auxThreadStats");
            writeThreadStats(header.getAuxThreadStats());
        }

        jg.writeEndObject();
    }

    private void writeEntries(List<Trace.Entry> entries, List<String> sharedQueryTexts)
            throws IOException {
        jg.writeStartArray();
        for (int i = 0; i < entries.size(); i++) {
            Trace.Entry entry = entries.get(i);
            int depth = entry.getDepth();
            jg.writeStartObject();
            writeEntry(entry, sharedQueryTexts);
            int nextDepth = i + 1 < entries.size() ? entries.get(i + 1).getDepth() : 0;
            if (nextDepth > depth) {
                jg.writeArrayFieldStart("childEntries");
            } else if (nextDepth < depth) {
                jg.writeEndObject();
                for (int j = depth; j > nextDepth; j--) {
                    jg.writeEndArray();
                    jg.writeEndObject();
                }
            } else {
                jg.writeEndObject();
            }
        }
        jg.writeEndArray();
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

    private void writeEntry(Trace.Entry entry, List<String> sharedQueryTexts) throws IOException {
        jg.writeNumberField("startOffsetNanos", entry.getStartOffsetNanos());
        jg.writeNumberField("durationNanos", entry.getDurationNanos());
        if (entry.getActive()) {
            jg.writeBooleanField("active", true);
        }
        if (entry.hasQueryEntryMessage()) {
            jg.writeObjectFieldStart("queryMessage");
            Trace.QueryEntryMessage queryMessage = entry.getQueryEntryMessage();
            jg.writeStringField("queryText",
                    sharedQueryTexts.get(queryMessage.getSharedQueryTextIndex()));
            jg.writeStringField("prefix", queryMessage.getPrefix());
            jg.writeStringField("suffix", queryMessage.getSuffix());
            jg.writeEndObject();
        } else {
            jg.writeStringField("message", entry.getMessage());
        }
        List<Trace.DetailEntry> detailEntries = entry.getDetailEntryList();
        if (!detailEntries.isEmpty()) {
            jg.writeFieldName("detail");
            writeDetailEntries(detailEntries);
        }
        List<Proto.StackTraceElement> locationStackTraceElements =
                entry.getLocationStackTraceElementList();
        if (!locationStackTraceElements.isEmpty()) {
            jg.writeArrayFieldStart("locationStackTraceElements");
            for (Proto.StackTraceElement stackTraceElement : locationStackTraceElements) {
                writeStackTraceElement(stackTraceElement);
            }
            jg.writeEndArray();
        }
        if (entry.hasError()) {
            jg.writeFieldName("error");
            writeError(entry.getError());
        }
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

    private void writeDetailEntries(List<Trace.DetailEntry> detailEntries) throws IOException {
        jg.writeStartObject();
        for (Trace.DetailEntry detailEntry : detailEntries) {
            jg.writeFieldName(detailEntry.getName());
            List<Trace.DetailEntry> childEntries = detailEntry.getChildEntryList();
            List<Trace.DetailValue> values = detailEntry.getValueList();
            if (!childEntries.isEmpty()) {
                writeDetailEntries(childEntries);
            } else if (values.size() == 1) {
                writeValue(values.get(0));
            } else if (values.size() > 1) {
                jg.writeStartArray();
                for (Trace.DetailValue value : values) {
                    writeValue(value);
                }
                jg.writeEndArray();
            } else {
                jg.writeNull();
            }
        }
        jg.writeEndObject();
    }

    private void writeValue(Trace.DetailValue value) throws IOException {
        switch (value.getValCase()) {
            case STRING:
                jg.writeString(value.getString());
                break;
            case DOUBLE:
                jg.writeNumber(value.getDouble());
                break;
            case LONG:
                jg.writeNumber(value.getLong());
                break;
            case BOOLEAN:
                jg.writeBoolean(value.getBoolean());
                break;
            default:
                throw new IllegalStateException("Unexpected detail value: " + value.getValCase());
        }
    }

    private void writeError(Trace.Error error) throws IOException {
        jg.writeStartObject();
        jg.writeStringField("message", error.getMessage());
        if (error.hasException()) {
            jg.writeFieldName("exception");
            writeThrowable(error.getException(), false);
        }
        jg.writeEndObject();
    }

    private void writeThrowable(Proto.Throwable throwable, boolean hasEnclosing)
            throws IOException {
        jg.writeStartObject();
        jg.writeStringField("className", throwable.getClassName());
        jg.writeStringField("message", throwable.getMessage());
        jg.writeArrayFieldStart("stackTraceElements");
        for (Proto.StackTraceElement stackTraceElement : throwable.getStackTraceElementList()) {
            writeStackTraceElement(stackTraceElement);
        }
        jg.writeEndArray();
        if (hasEnclosing) {
            jg.writeNumberField("framesInCommonWithEnclosing",
                    throwable.getFramesInCommonWithEnclosing());
        }
        if (throwable.hasCause()) {
            jg.writeFieldName("cause");
            writeThrowable(throwable.getCause(), true);
        }
        jg.writeEndObject();
    }

    private void writeStackTraceElement(Proto.StackTraceElement stackTraceElement)
            throws IOException {
        jg.writeString(new StackTraceElement(stackTraceElement.getClassName(),
                stackTraceElement.getMethodName(), stackTraceElement.getFileName(),
                stackTraceElement.getLineNumber()).toString());
    }

    private void writeFlattenedTimers(Trace.Timer rootTimer) throws IOException {
        Map<String, FlattenedTimer> flattenedTimers = new HashMap<String, FlattenedTimer>();
        flattenTimer(rootTimer, flattenedTimers, new HashSet<String>());
        writeFlattenedTimers(flattenedTimers);
    }

    // need to keep track of parent timer names since the same timer can be nested underneath itself
    // when separated by another timer, e.g. abc > xyz > abc
    private void flattenTimer(Trace.Timer timer, Map<String, FlattenedTimer> flattenedTimers,
            Set<String> parentTimerNames) {
        FlattenedTimer flattenedTimer = flattenedTimers.get(timer.getName());
        if (flattenedTimer == null) {
            flattenedTimer = new FlattenedTimer();
            flattenedTimers.put(timer.getName(), flattenedTimer);
        }
        flattenedTimer.totalNanos += timer.getTotalNanos();
        flattenedTimer.count += timer.getCount();
        List<Trace.Timer> childTimers = timer.getChildTimerList();
        if (!childTimers.isEmpty()) {
            parentTimerNames.add(timer.getName());
            for (Trace.Timer childTimer : childTimers) {
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

    private void writeAsyncTimers(List<Trace.Timer> asyncTimers) throws IOException {
        for (Trace.Timer asyncTimer : asyncTimers) {
            jg.writeStartObject();
            jg.writeStringField("name", asyncTimer.getName());
            jg.writeNumberField("totalNanos", asyncTimer.getTotalNanos());
            jg.writeNumberField("count", asyncTimer.getCount());
            jg.writeEndObject();
        }
    }

    private void writeThreadStats(Trace.ThreadStats threadStats) throws IOException {
        jg.writeStartObject();
        jg.writeNumberField("cpuNanos", threadStats.getCpuNanos());
        jg.writeNumberField("blockedNanos", threadStats.getBlockedNanos());
        jg.writeNumberField("waitedNanos", threadStats.getWaitedNanos());
        jg.writeNumberField("allocatedBytes", threadStats.getAllocatedBytes());
        jg.writeEndObject();
    }

    private static class FlattenedTimer {
        private long totalNanos;
        private long count;
    }
}
