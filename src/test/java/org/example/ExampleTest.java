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

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.Test;

import org.glowroot.agent.collector.Collector;
import org.glowroot.agent.collector.Collector.AgentConfigUpdater;
import org.glowroot.agent.collector.Collector.TraceReader;
import org.glowroot.agent.collector.Collector.TraceVisitor;
import org.glowroot.agent.shaded.com.google.common.io.BaseEncoding;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.AggregateOuterClass.Aggregate;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValueMessage.GaugeValue;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.CollectorServiceOuterClass.InitMessage.Environment;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.Proto;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.TraceOuterClass.Trace;
import org.glowroot.agent.shaded.org.glowroot.wire.api.model.TraceOuterClass.Trace.QueryEntryMessage;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

public class ExampleTest {

    private static final Random random = new Random();

    @Test
    public void test() throws Exception {
        Collector collector = new ExampleCollector();

        collector.init(new ArrayList<File>(), Environment.getDefaultInstance(),
                AgentConfig.getDefaultInstance(), new NopAgentConfigUpdater());

        collector.collectTrace(new TraceReaderImpl(1000000 + random.nextInt(2000000000)));

        List<GaugeValue> gaugeValues = new ArrayList<GaugeValue>();
        gaugeValues.add(createGaugeValue("java.lang:type=Memory:HeapMemoryUsage.used"));
        gaugeValues.add(createGaugeValue("java.lang:type=OperatingSystem:ProcessCpuLoad"));
        gaugeValues.add(createGaugeValue("java.lang:type=OperatingSystem:SystemCpuLoad"));
        collector.collectGaugeValues(gaugeValues);
    }

    private static GaugeValue createGaugeValue(String name) throws InterruptedException {
        // separate gauge values by some amount to emphasize that they do not always share the same
        // millisecond capture time
        MICROSECONDS.sleep(800);
        return GaugeValue.newBuilder()
                .setGaugeName(name)
                .setCaptureTime(System.currentTimeMillis())
                .setValue(random.nextDouble())
                .setWeight(1)
                .build();
    }

    private static class TraceReaderImpl implements TraceReader {

        private final long durationNanos;
        private final long captureTime;
        private final long startTime;
        private final String traceId;

        private TraceReaderImpl(long durationNanos) {
            this.durationNanos = durationNanos;
            captureTime = System.currentTimeMillis();
            startTime = captureTime - durationNanos;
            traceId = buildTraceId(startTime);
        }

        @Override
        public long captureTime() {
            return System.currentTimeMillis();
        }

        @Override
        public String traceId() {
            return traceId;
        }

        @Override
        public boolean partial() {
            return false;
        }

        @Override
        public boolean update() {
            return false;
        }

        @Override
        public void accept(TraceVisitor traceVisitor) throws Exception {
            traceVisitor.visitEntry(Trace.Entry.newBuilder()
                    .setDepth(0)
                    .setStartOffsetNanos(6)
                    .setDurationNanos(5)
                    .setMessage("a non query entry")
                    .build());
            traceVisitor.visitEntry(Trace.Entry.newBuilder()
                    .setDepth(0)
                    .setStartOffsetNanos(20)
                    .setDurationNanos(50)
                    .setMessage("a parent entry")
                    .build());
            traceVisitor.visitEntry(Trace.Entry.newBuilder()
                    .setDepth(1)
                    .setStartOffsetNanos(21)
                    .setDurationNanos(10)
                    .setQueryEntryMessage(QueryEntryMessage.newBuilder()
                            .setSharedQueryTextIndex(0)
                            .setPrefix("jdbc execute: ")
                            .setSuffix(" [61279943, 'a bind var'] => 0 rows"))
                    .build());
            traceVisitor.visitQueries(Arrays.asList(Aggregate.Query.newBuilder()
                    .setType("SQL")
                    .setSharedQueryTextIndex(0)
                    .setTotalDurationNanos(10)
                    .setExecutionCount(1)
                    .setTotalRows(Proto.OptionalInt64.newBuilder().setValue(0))
                    .build()));
            traceVisitor.visitSharedQueryTexts(Arrays.asList("select 1 from table"));
            traceVisitor.visitHeader(readHeader());
        }

        @Override
        public Trace.Header readHeader() {
            long auxThreadDuration = random(durationNanos);
            long asyncTimerDuration = random(durationNanos);
            return Trace.Header.newBuilder()
                    .setTransactionType("Web")
                    .setHeadline("/the/url")
                    .setStartTime(startTime)
                    .setCaptureTime(captureTime)
                    .setDurationNanos(durationNanos)
                    .addAllDetailEntry(createDetailEntry("Request remote address", "10.10.10.10"))
                    .addAllDetailEntry(createDetailEntry("Request remote port", 12345))
                    .addAllDetailEntry(createDetailEntry("Request local address", "9.9.9.9"))
                    .addAllDetailEntry(createDetailEntry("Request local hostname", "host1"))
                    .addAllDetailEntry(createDetailEntry("Request local port", 8080))
                    .addAllDetailEntry(createDetailEntry("Request http method", "POST"))
                    .addAllDetailEntry(createDetailEntry("Request query string",
                            "the=query&the=string&another"))
                    .addAllDetailEntry(Arrays.asList(Trace.DetailEntry.newBuilder()
                            .setName("Request headers")
                            .addAllChildEntry(createDetailEntry("User-Agent", "Mozilla!"))
                            .addAllChildEntry(createDetailEntry("Content-Length", "25"))
                            .build()))
                    .addAllDetailEntry(Arrays.asList(Trace.DetailEntry.newBuilder()
                            .setName("Request parameters")
                            .addAllChildEntry(createDetailEntry("abc", "123"))
                            .addAllChildEntry(createDetailEntry("and", "some"))
                            .addAllChildEntry(createDetailEntry("and", "more"))
                            .build()))
                    .addAllDetailEntry(Arrays.asList(Trace.DetailEntry.newBuilder()
                            .setName("Response headers")
                            .addAllChildEntry(createDetailEntry("Content-Length", "5012"))
                            .build()))
                    .addAllDetailEntry(createDetailEntry("Response code", 200))
                    .setMainThreadRootTimer(Trace.Timer.newBuilder()
                            .setName("http request")
                            .setTotalNanos(durationNanos)
                            .setCount(1)
                            .addAllChildTimer(Arrays.asList(Trace.Timer.newBuilder()
                                    .setName("jdbc execute")
                                    .setTotalNanos(random(durationNanos))
                                    .setCount(random.nextInt(10))
                                    .build()))
                            .build())
                    .setAuxThreadRootTimer(Trace.Timer.newBuilder()
                            .setName("auxiliary thread")
                            .setTotalNanos(auxThreadDuration)
                            .setCount(1)
                            .addAllChildTimer(Arrays.asList(Trace.Timer.newBuilder()
                                    .setName("jdbc execute")
                                    .setTotalNanos(random(auxThreadDuration))
                                    .setCount(random.nextInt(10))
                                    .build()))
                            .build())
                    .addAllAsyncTimer(Arrays.asList(Trace.Timer.newBuilder()
                            .setName("http request")
                            .setTotalNanos(asyncTimerDuration)
                            .setCount(random.nextInt(2))
                            .build()))
                    .setMainThreadStats(Trace.ThreadStats.newBuilder()
                            .setCpuNanos(random(durationNanos / 3))
                            .setWaitedNanos(random(durationNanos / 3))
                            .setBlockedNanos(random(durationNanos / 3))
                            .setAllocatedBytes(random.nextInt(1000000))
                            .build())
                    .setAuxThreadStats(Trace.ThreadStats.newBuilder()
                            .setCpuNanos(random(durationNanos / 3))
                            .setWaitedNanos(random(durationNanos / 3))
                            .setBlockedNanos(random(durationNanos / 3))
                            .setAllocatedBytes(random.nextInt(1000000))
                            .build())
                    .build();
        }

        private List<Trace.DetailEntry> createDetailEntry(String name, String value) {
            Trace.DetailEntry.Builder builder = Trace.DetailEntry.newBuilder()
                    .setName(name);
            builder.addValueBuilder()
                    .setString(value);
            return Arrays.asList(builder.build());
        }

        private List<Trace.DetailEntry> createDetailEntry(String name, long value) {
            Trace.DetailEntry.Builder builder = Trace.DetailEntry.newBuilder()
                    .setName(name);
            builder.addValueBuilder()
                    .setLong(value);
            return Arrays.asList(builder.build());
        }

        private static long random(long value) {
            return (long) (value * random.nextDouble());
        }

        // copied from org.glowroot.agent.impl.Transaction
        private static String buildTraceId(long startTime) {
            byte[] bytes = new byte[10];
            random.nextBytes(bytes);
            // lower 6 bytes of current time will wrap only every 8925 years
            return lowerSixBytesHex(startTime) + BaseEncoding.base16().lowerCase().encode(bytes);
        }

        // copied from org.glowroot.agent.impl.Transaction
        private static String lowerSixBytesHex(long startTime) {
            long mask = 1L << 48;
            return Long.toHexString(mask | (startTime & (mask - 1))).substring(1);
        }
    }

    private static class NopAgentConfigUpdater implements AgentConfigUpdater {
        @Override
        public void update(AgentConfig agentConfig) throws IOException {}
    }
}
