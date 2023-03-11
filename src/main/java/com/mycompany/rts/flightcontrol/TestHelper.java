package com.mycompany.rts.flightcontrol;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtils;
import org.jfree.chart.JFreeChart;
import org.jfree.data.category.DefaultCategoryDataset;

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

    public void printLineChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        for (int i = 0; i < durationList.size(); i++) {
            dataset.addValue(Double.parseDouble(durationList.get(i)), "Duration", String.valueOf(i));
        }

        JFreeChart lineChart = ChartFactory.createLineChart(
                "Duration Metrics", // Chart Title
                "Iteration", // X-Axis Label
                "Duration (ms)", // Y-Axis Label
                dataset);

        // print chart to jpeg
        try {
            ChartUtils.saveChartAsJPEG(new File("LineChart.jpeg"), lineChart, 500, 300);
        } catch (IOException e) {
        }
    }

}
