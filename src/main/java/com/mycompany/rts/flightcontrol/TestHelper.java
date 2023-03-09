package com.mycompany.rts.flightcontrol;

import java.util.ArrayList;

public class TestHelper {
    public volatile ArrayList<String> durationList = new ArrayList<String>();
    public volatile long startTime = 0;
    public volatile long endTime = 0;
    public volatile int cycles = 0;
    public volatile int totalConsumed = 0;
    public volatile int totalPublished = 0;

    public synchronized String getTimeDifference() {
        return String.valueOf(endTime - startTime);
    }

    public synchronized void addDuration(String duration) {
        durationList.add(duration);
    }

    public void printDurationMetrics() {
        long totalDuration = 0;
        long maxDuration = 0;
        long minDuration = 100000;
        long totalSignals = durationList.size();

        for (String duration : durationList) {
            long durationParsed = Long.parseLong(duration);
            if (durationParsed > maxDuration) {
                maxDuration = durationParsed;
            }
            if (durationParsed < minDuration) {
                minDuration = durationParsed;
            }
            totalDuration += durationParsed;
        }
        System.out.println();
        System.out.println("==================================");
        System.out.println("Duration Metrics");
        System.out.println("==================================");
        System.out.println("Total Duration: " + totalDuration + " ms - over " + cycles + " cycles");
        System.out.println("Max Duration: " + maxDuration + " ms");
        System.out.println("Min Duration: " + minDuration + " ms");
        System.out.println("Avg Duration: " + totalDuration / totalSignals + " ms");
        System.out.println("==================================");
        System.out.println();
    }

    public void printThroughputMetrics() {
        System.out.println();
        System.out.println("==================================");
        System.out.println("Throughput Metrics");
        System.out.println("==================================");
        System.out.println("Total Consumed: " + totalConsumed + " messages");
        System.out.println("Total Published: " + totalPublished + " messages");
        System.out.println("==================================");
        System.out.println();
    }
}
