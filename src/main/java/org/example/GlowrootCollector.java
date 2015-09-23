package org.example;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.glowroot.collector.spi.model.AggregateOuterClass.Aggregate;
import org.glowroot.collector.spi.model.GaugeValueOuterClass.GaugeValue;
import org.glowroot.collector.spi.model.TraceOuterClass.Trace;
import org.glowroot.collector.spi.model.TraceOuterClass.Trace.DetailEntry;
import org.glowroot.collector.spi.model.TraceOuterClass.Trace.Timer;

public class GlowrootCollector implements org.glowroot.collector.spi.Collector {

    public void collectTrace(Trace trace) throws Exception {
        System.out.println("========================================");
        Trace.Header header = trace.getHeader();
        System.out.println("header:");
        System.out.println("  id: " + header.getId());
        System.out.println("  partial: " + header.getPartial());
        System.out.println("  slow: " + header.getSlow());
        System.out.println("  start time: " + new Date(header.getStartTime()));
        System.out.println("  capture time: " + new Date(header.getCaptureTime()));
        System.out.println("  duration millis: " + header.getDurationNanos() / 1000000.0);
        System.out.println("  transaction type: " + header.getTransactionType());
        System.out.println("  transaction name: " + header.getTransactionName());
        System.out.println("  headline: " + header.getHeadline());
        System.out.println("  user: " + header.getUser());
        List<Trace.Attribute> attributes = header.getAttributeList();
        if (!attributes.isEmpty()) {
            System.out.println("  attributes:");
            for (Trace.Attribute attribute : attributes) {
                printAttribute(attribute, "    ");
            }
        }
        List<DetailEntry> detailEntries = header.getDetailEntryList();
        if (!detailEntries.isEmpty()) {
            System.out.println("  detail:");
            printDetailEntries(detailEntries, "    ");
        }
        if (header.hasError()) {
            System.out.println("  error:");
            writeError(header.getError(), "    ");
        }
        System.out.println("  timers:");
        Trace.Timer rootTimer = header.getRootTimer();
        printTimer(rootTimer, "    ");
        long threadCpuNanos = header.getThreadCpuNanos();
        if (threadCpuNanos != -1) {
            System.out.println("  thread cpu millis: " + threadCpuNanos / 1000000.0);
        }
        long threadBlockedNanos = header.getThreadBlockedNanos();
        if (threadBlockedNanos != -1) {
            System.out.println("  thread blocked millis: " + threadBlockedNanos / 1000000.0);
        }
        long threadWaitedNanos = header.getThreadWaitedNanos();
        if (threadWaitedNanos != -1) {
            System.out.println("  thread waited millis: " + threadWaitedNanos / 1000000.0);
        }
        long threadAllocatedBytes = header.getThreadAllocatedBytes();
        if (threadAllocatedBytes != -1) {
            System.out.println("  thread allocated bytes: " + threadAllocatedBytes);
        }
        List<Trace.GarbageCollectionActivity> gcActivities = header.getGcActivityList();
        if (!gcActivities.isEmpty()) {
            System.out.println("  gc activities:");
            for (Trace.GarbageCollectionActivity gcActivity : gcActivities) {
                System.out.println("    collector name: " + gcActivity.getCollectorName());
                System.out.println("    total millis: " + gcActivity.getTotalMillis());
                System.out.println("    count: " + gcActivity.getCount());
            }
        }
        System.out.println("  entry count: " + header.getEntryCount());
        boolean entryLimitExceeded = header.getEntryLimitExceeded();
        if (entryLimitExceeded) {
            System.out.println("  entry limit exceeded: true");
        }
        System.out.println("  profile sample count: " + header.getProfileSampleCount());
        boolean profileSampleLimitExceeded = header.getProfileSampleLimitExceeded();
        if (profileSampleLimitExceeded) {
            System.out.println("  entry limit exceeded: true");
        }
        System.out.println("========================================");
    }

    public void collectAggregates(Map<String, Aggregate> overallAggregates,
            Map<String, Map<String, Aggregate>> transactionAggregates, long captureTime)
                    throws Exception {
        System.out.println("collectAggregates");
    }

    public void collectGaugeValues(Map<String, GaugeValue> gaugeValues) throws Exception {
        System.out.println("collectGaugeValues: " + gaugeValues.size());
    }

    private static void printAttribute(Trace.Attribute attribute, String indent) {
        System.out.print(indent + attribute.getName() + ": ");
        boolean first = true;
        for (String value : attribute.getValueList()) {
            if (!first) {
                System.out.print(", ");
            }
            System.out.println(value);
            first = false;
        }
    }

    private static void printDetailEntries(List<Trace.DetailEntry> detailEntries, String indent)
            throws IOException {
        for (Trace.DetailEntry detailEntry : detailEntries) {
            System.out.print(indent + detailEntry.getName() + ":");
            List<Trace.DetailEntry> childEntries = detailEntry.getChildEntryList();
            List<Trace.DetailValue> values = detailEntry.getValueList();
            if (!childEntries.isEmpty()) {
                System.out.println();
                printDetailEntries(childEntries, indent + "  ");
            } else if (!values.isEmpty()) {
                System.out.print(" ");
                boolean first = true;
                for (Trace.DetailValue value : values) {
                    if (!first) {
                        System.out.print(", ");
                    }
                    printValue(value);
                    first = false;
                }
                System.out.println();
            }
        }
    }

    private static void printValue(Trace.DetailValue value) throws IOException {
        switch (value.getValCase()) {
            case SVAL:
                System.out.print(value.getSval());
                break;
            case DVAL:
                System.out.print(value.getDval());
                break;
            case LVAL:
                System.out.print(value.getLval());
                break;
            case BVAL:
                System.out.print(value.getBval());
                break;
            default:
                throw new IllegalStateException(
                        "Unexpected detail value: " + value.getValCase());
        }
    }

    private static void writeError(Trace.Error error, String indent) {
        System.out.println(indent + "message: " + error.getMessage());
        if (error.hasException()) {
            System.out.println(indent + "exception:");
            Trace.Throwable exception = error.getException();
            printThrowable(exception, indent + "  ");
        }
    }

    private static void printThrowable(Trace.Throwable exception, String indent) {
        System.out.println(indent + "display: " + exception.getDisplay());
        System.out.println(indent + "stack trace:");
        for (Trace.StackTraceElement stackTraceElement : exception.getElementList()) {
            printStackTraceElement(stackTraceElement, indent + "  ");
        }
        if (exception.hasCause()) {
            System.out.println(indent + "frames in common with enclosing: "
                    + exception.getFramesInCommonWithEnclosing());
            System.out.println(indent + "cause:");
            printThrowable(exception.getCause(), indent + "  ");
        }
    }

    private static void printTimer(Trace.Timer timer, String indent) {
        System.out.println(indent + "name: " + timer.getName());
        boolean extended = timer.getExtended();
        if (extended) {
            System.out.println(indent + "extended: true");
        }
        System.out.println(indent + "total millis: " + timer.getTotalNanos() / 1000000.0);
        System.out.println(indent + "count: " + timer.getCount());
        boolean active = timer.getActive();
        if (active) {
            System.out.println(indent + "active: true");
        }
        List<Timer> childTimers = timer.getChildTimerList();
        if (!childTimers.isEmpty()) {
            System.out.println(indent + "child timers:");
            for (Trace.Timer childTimer : childTimers) {
                printTimer(childTimer, indent + "  ");
            }
        }
    }

    private static void printStackTraceElement(Trace.StackTraceElement stackTraceElement,
            String indent) {
        StackTraceElement javaLangStackTraceElement = new StackTraceElement(
                stackTraceElement.getClassName(), stackTraceElement.getMethodName(),
                stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
        System.out.println(indent + javaLangStackTraceElement.toString());
    }
}
