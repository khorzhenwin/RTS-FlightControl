package com.mycompany.rts.flightcontrol;

import java.util.ArrayList;

public class TestHelper {
    public volatile ArrayList<String> durationList = new ArrayList<String>();
    public volatile double startTime = 0;
    public volatile double endTime = 0;
    public volatile int cycles = 0;
    public volatile int totalConsumed = 0;
    public volatile int totalPublished = 0;

    public synchronized String getTimeDifference() {
        return String.valueOf(endTime - startTime);
    }

    public synchronized void addDuration(String duration) {
        durationList.add(duration);
    }

    public void printDurationMetrics(String metricType) {
        double totalDuration = 0;
        double maxDuration = 0;
        double minDuration = 100000;
        double totalSignals = durationList.size();

        for (String duration : durationList) {
            double durationParsed = Double.parseDouble(duration);
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
        System.out.println("Duration Metrics For " + metricType);
        System.out.println("==================================");
        System.out.println("Total Duration: " + totalDuration + " ms - over " + cycles + " iterations");
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
