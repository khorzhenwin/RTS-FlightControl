package com.mycompany.rts.flightcontrol;

import java.util.ArrayList;

public class TestHelper {
    public int totalConsumed = 0;
    public int totalPublished = 0;

    public void printDurationMetrics(ArrayList<String> durationList) {
        long totalDuration = 0;
        long maxDuration = 0;
        long minDuration = 0;
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

        System.out.println("Duration Metrics");
        System.out.println("==================================");
        System.out.println("Total Signals: " + totalSignals);
        System.out.println("Total Duration: " + totalDuration);
        System.out.println("Max Duration: " + maxDuration);
        System.out.println("Min Duration: " + minDuration);
        System.out.println("Avg Duration: " + totalDuration / totalSignals);
        System.out.println("==================================");
    }

    public void printThroughputMetrics() {
        System.out.println("Throughput Metrics");
        System.out.println("==================================");
        System.out.println("Total Consumed: " + totalConsumed);
        System.out.println("Total Published: " + totalPublished);
        System.out.println("==================================");
    }
}
