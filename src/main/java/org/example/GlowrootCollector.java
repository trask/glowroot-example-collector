package org.example;

import java.util.Collection;
import java.util.Map;

import org.glowroot.collector.spi.Aggregate;
import org.glowroot.collector.spi.GaugePoint;
import org.glowroot.collector.spi.Trace;

public class GlowrootCollector implements org.glowroot.collector.spi.Collector {

    public void collectTrace(Trace trace) throws Exception {
        System.out.println("collectTrace: " + trace);
    }

    public void collectAggregates(Map<String, ? extends Aggregate> overallAggregates,
            Map<String, ? extends Map<String, ? extends Aggregate>> transactionAggregates,
            long captureTime) throws Exception {
        System.out.println("collectAggregates");
    }

    public void collectGaugePoints(Collection<? extends GaugePoint> gaugePoints) throws Exception {
        System.out.println("collectGaugePoints: " + gaugePoints.size());
    }
}
