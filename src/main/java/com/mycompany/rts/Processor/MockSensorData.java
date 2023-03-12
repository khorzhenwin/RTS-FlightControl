package com.mycompany.rts.Processor;

import java.util.ArrayList;

import com.mycompany.rts.Helper.PublisherHelper;
import com.mycompany.rts.Helper.TestHelper;

public class MockSensorData extends TestHelper {

    public volatile boolean isSuddenLossOfPressure = false;
    public volatile boolean isLandingMode = false;
    public volatile ArrayList<String> sensorDataList = new ArrayList<String>();
    public volatile String[] changeTypes = { "increased", "decreased" };

    public String getRandomChangeType() {
        return changeTypes[(int) (Math.random() * changeTypes.length)];
    }

    public String getMeasurementUnit(String sensorType) {
        String measurementUnit = "";
        switch (sensorType) {
            case "altitude":
                measurementUnit = "ft";
                break;
            case "cabinPressure":
                measurementUnit = "%";
                break;
            case "speed":
                measurementUnit = "km/h";
                break;
            case "rain":
                measurementUnit = "%";
                break;
        }
        return measurementUnit;
    }

    public int getRandomChangeValue(String sensorType) {
        int changeValue = 0;
        switch (sensorType) {
            case "altitude":
                changeValue = (int) (Math.random() * 3000) + 1000;
                break;
            case "cabinPressure":
                changeValue = (int) (Math.random() * 30);
                break;
            case "speed":
                changeValue = (int) (Math.random() * 50);
                break;
            case "rain":
                changeValue = (int) (Math.random() * 30);
                break;
        }
        return changeValue;
    }

    public class SensorDataGenerator implements Runnable {

        String sensorType;

        public SensorDataGenerator(String sensorType) {
            this.sensorType = sensorType;
        }

        @Override
        public void run() {
            // 1 in 5 chance of sudden loss of pressure and will only happen once
            if (sensorType.equals("cabinPressure") && ((int) (Math.random() * 5) == 0) && !isSuddenLossOfPressure) {
                System.out.println("Generated : " + sensorType + " " + "sudden loss of pressure");
                sensorDataList.add(sensorType + " decreased 50");
                isSuddenLossOfPressure = true;
            } else {
                String changeType = getRandomChangeType();
                int changeValue = getRandomChangeValue(sensorType);
                System.out.println("Generated : " + sensorType + " " + changeType + " by " + changeValue + " "
                        + getMeasurementUnit(sensorType));
                // format eg "altitude increased 1000"
                sensorDataList.add(sensorType + " " + changeType + " " + changeValue);
            }
        }
    }

    public class SensorDataPublisher extends PublisherHelper implements Runnable {

        public SensorDataPublisher(String publisherExchange, String publisherKey, String exchangeType) {
            super(publisherExchange, publisherKey, exchangeType);
        }

        @Override
        public void run() {
            try {
                System.out.println("Publishing " + sensorDataList.size() + " sensor data");
                endTime = 0;
                startTime = System.currentTimeMillis();
                for (int i = 0; i < sensorDataList.size(); i++) {
                    publish(sensorDataList.get(i));
                    if (!sensorDataList.get(i).contains("sensor new reading")) {
                        totalPublished++;
                    }
                }
                sensorDataList.clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
