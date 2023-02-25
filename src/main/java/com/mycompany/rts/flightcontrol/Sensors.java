package com.mycompany.rts.flightcontrol;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Sensors {
    protected static final String EXCHANGE_NAME = "flight_control";
    protected static final String PUBLISHER_ROUTING_KEY = "sensor.data";

    public static void main(String[] args) throws IOException, TimeoutException {
        MockSensorData mockSensorData = new MockSensorData(EXCHANGE_NAME, PUBLISHER_ROUTING_KEY, "topic");
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(mockSensorData, 10, 10, TimeUnit.SECONDS);
    }
}

class MockSensorData extends PublisherHelper implements Runnable {

    public MockSensorData(String publisherExchange, String publisherKey, String exchangeType) {
        super(publisherExchange, publisherKey, exchangeType);
    }

    @Override
    public void run() {
        try {
            String sensorType = getRandomSensorType();
            String changeType = getRandomChangeType();
            int changeValue = getRandomChangeValue(sensorType);
            System.out.println("Detected : " + sensorType + " " + changeType + " by " + changeValue + " "
                    + getMeasurementUnit(sensorType));
            publish(sensorType + " " + changeType + " " + changeValue); // format eg "altitude increased 1000"
            System.out.println();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String[] sensorTypes = { "altitude", "cabinPressure", "speed", "rain" };
    public String[] changeTypes = { "increased", "decreased" };

    public String getRandomSensorType() {
        return sensorTypes[(int) (Math.random() * sensorTypes.length)];
    }

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
                changeValue = (int) (Math.random() * 10);
                break;
            case "speed":
                changeValue = (int) (Math.random() * 20);
                break;
            case "rain":
                changeValue = (int) (Math.random() * 10);
                break;
        }
        return changeValue;
    }

}
