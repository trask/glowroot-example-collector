package org.example;

import java.util.Map;

import org.glowroot.collector.spi.model.AggregateOuterClass.Aggregate;
import org.glowroot.collector.spi.model.GaugeValueOuterClass.GaugeValue;
import org.glowroot.collector.spi.model.TraceOuterClass.Trace;

public class GlowrootCollector implements org.glowroot.collector.spi.Collector {

    public void collectTrace(Trace trace) throws Exception {
        System.out.println("collectTrace: " + trace);
    }

    public void collectAggregates(Map<String, Aggregate> overallAggregates,
            Map<String, Map<String, Aggregate>> transactionAggregates, long captureTime)
                    throws Exception {
        System.out.println("collectAggregates");
    }

    public void collectGaugeValues(Map<String, GaugeValue> gaugeValues) throws Exception {
        System.out.println("collectGaugeValues: " + gaugeValues.size());
    }
}
