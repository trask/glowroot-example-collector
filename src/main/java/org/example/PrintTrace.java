/*
 * Copyright 2015 the original author or authors.
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
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.glowroot.collector.spi.model.ProfileTreeOuterClass.ProfileTree;
import org.glowroot.collector.spi.model.TraceOuterClass.Trace;

class PrintTrace {

    static void printTrace(Trace trace) throws Exception {
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
        List<Trace.DetailEntry> detailEntries = header.getDetailEntryList();
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
        Iterator<Trace.GarbageCollectionActivity> gcActivities =
                header.getGcActivityList().iterator();
        if (gcActivities.hasNext()) {
            System.out.println("  gc activities:");
            while (gcActivities.hasNext()) {
                Trace.GarbageCollectionActivity gcActivity = gcActivities.next();
                System.out.println("    collector name: " + gcActivity.getCollectorName());
                System.out.println("    total millis: " + gcActivity.getTotalMillis());
                System.out.println("    count: " + gcActivity.getCount());
                if (gcActivities.hasNext()) {
                    System.out.println("    --------------------");
                }
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
        Iterator<Trace.Entry> entries = trace.getEntryList().iterator();
        if (entries.hasNext()) {
            System.out.println("entries:");
            printEntries(entries, "  ");
        }
        ProfileTree profileTree = trace.getProfileTree();
        if (!profileTree.getNodeList().isEmpty()) {
            System.out.println("profile:");
            PrintProfileTree.printProfileTree(profileTree);
        }
    }

    private static void printAttribute(Trace.Attribute attribute, String indent) {
        StringBuffer sb = new StringBuffer();
        sb.append(indent + attribute.getName() + ": ");
        Iterator<String> values = attribute.getValueList().iterator();
        while (values.hasNext()) {
            sb.append(values.next());
            if (values.hasNext()) {
                sb.append(", ");
            }
        }
        System.out.println(sb);
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
        Iterator<Trace.Timer> childTimers = timer.getChildTimerList().iterator();
        if (childTimers.hasNext()) {
            System.out.println(indent + "child timers:");
            while (childTimers.hasNext()) {
                Trace.Timer childTimer = childTimers.next();
                printTimer(childTimer, indent + "  ");
                if (childTimers.hasNext()) {
                    System.out.println(indent + "  --------------------");
                }
            }
        }
    }

    private static void printEntries(Iterator<Trace.Entry> entries, String indent)
            throws IOException {
        while (entries.hasNext()) {
            Trace.Entry entry = entries.next();
            System.out.println(
                    indent + "start offset millis: " + entry.getStartOffsetNanos() / 1000000.0);
            System.out.println(indent + "duration millis: " + entry.getDurationNanos() / 1000000.0);
            boolean active = entry.getActive();
            if (active) {
                System.out.println(indent + "active: true");
            }
            System.out.println(indent + "message: " + entry.getMessage());
            List<Trace.DetailEntry> detailEntries = entry.getDetailEntryList();
            if (!detailEntries.isEmpty()) {
                System.out.println(indent + "detail:");
                printDetailEntries(detailEntries, indent + "  ");
            }
            List<Trace.StackTraceElement> locationStackTraceElements =
                    entry.getLocationStackTraceElementList();
            if (!locationStackTraceElements.isEmpty()) {
                System.out.println(indent + "location stack trace:");
                for (Trace.StackTraceElement stackTraceElement : locationStackTraceElements) {
                    printStackTraceElement(stackTraceElement, indent + "  ");
                }
            }
            if (entry.hasError()) {
                System.out.println(indent + "error:");
                writeError(entry.getError(), indent + "  ");
            }
            Iterator<Trace.Entry> childEntries = entry.getChildEntryList().iterator();
            if (childEntries.hasNext()) {
                System.out.println(indent + "child entries:");
                printEntries(childEntries, indent + "  ");
            }
            if (entries.hasNext()) {
                System.out.println(indent + "--------------------");
            }
        }
    }

    private static void printDetailEntries(List<Trace.DetailEntry> detailEntries, String indent)
            throws IOException {
        for (Trace.DetailEntry detailEntry : detailEntries) {
            StringBuffer sb = new StringBuffer();
            sb.append(indent + detailEntry.getName() + ":");
            List<Trace.DetailEntry> childEntries = detailEntry.getChildEntryList();
            if (!childEntries.isEmpty()) {
                System.out.println(sb);
                printDetailEntries(childEntries, indent + "  ");
                continue;
            }
            Iterator<Trace.DetailValue> values = detailEntry.getValueList().iterator();
            if (values.hasNext()) {
                sb.append(' ');
                while (values.hasNext()) {
                    appendValue(sb, values.next());
                    if (values.hasNext()) {
                        sb.append(", ");
                    }
                }
                System.out.println(sb);
            }
        }
    }

    private static void appendValue(StringBuffer sb, Trace.DetailValue value) throws IOException {
        switch (value.getValCase()) {
            case SVAL:
                sb.append(value.getSval());
                break;
            case DVAL:
                sb.append(value.getDval());
                break;
            case LVAL:
                sb.append(value.getLval());
                break;
            case BVAL:
                sb.append(value.getBval());
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

    private static void printStackTraceElement(Trace.StackTraceElement stackTraceElement,
            String indent) {
        StackTraceElement javaLangStackTraceElement = new StackTraceElement(
                stackTraceElement.getClassName(), stackTraceElement.getMethodName(),
                stackTraceElement.getFileName(), stackTraceElement.getLineNumber());
        System.out.println(indent + javaLangStackTraceElement.toString());
    }
}
