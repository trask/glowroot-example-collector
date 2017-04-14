/*
 * Copyright 2015-2017 the original author or authors.
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
import java.util.List;

import org.glowroot.agent.shaded.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig;
import org.glowroot.agent.shaded.glowroot.wire.api.model.CollectorServiceOuterClass.Environment;
import org.glowroot.agent.shaded.glowroot.wire.api.model.CollectorServiceOuterClass.GaugeValue;
import org.glowroot.agent.shaded.glowroot.wire.api.model.CollectorServiceOuterClass.LogEvent;

public class GlowrootExampleCollector implements org.glowroot.agent.collector.Collector {

    @Override
    public void init(File glowrootDir, File agentDir, Environment environment,
            AgentConfig agentConfig, AgentConfigUpdater agentConfigUpdater) throws Exception {
        System.out.println("collectInit");
    }

    @Override
    public void collectAggregates(AggregateReader aggregateReader) throws Exception {
        System.out.println("collectAggregates");
    }

    @Override
    public void collectGaugeValues(List<GaugeValue> gaugeValues) throws Exception {
        System.out.println("collectGaugeValues: " + gaugeValues.size());
    }

    @Override
    public void collectTrace(TraceReader traceReader) throws Exception {
        System.out.println("collectTrace");
    }

    @Override
    public void log(LogEvent logEvent) throws Exception {
        System.out.println("log");
    }
}
