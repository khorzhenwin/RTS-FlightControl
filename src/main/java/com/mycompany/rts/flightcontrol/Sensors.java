package com.mycompany.rts.flightcontrol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class Sensors {
    protected static final String EXCHANGE_NAME = "flight_control";
    protected static final String PUBLISHER_ROUTING_KEY = "sensor.data";
    protected static final String CONSUMER_ROUTING_KEY = "sensor.update";

    public static void main(String[] args) throws IOException, TimeoutException {
        // "altitude", "cabinPressure", "speed", "rain"
        MockSensorData mockSensorData = new MockSensorData();
        String[] sensorTypes = { "altitude", "cabinPressure", "speed", "rain" };
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
        // new data every 4 seconds
        for (String sensorType : sensorTypes) {
            executor.scheduleAtFixedRate(mockSensorData.new SensorDataGenerator(sensorType), 4, 4, TimeUnit.SECONDS);
        }
        // publish every 5 seconds, initial delay 5 seconds
        executor.scheduleAtFixedRate(mockSensorData.new SensorDataPublisher(EXCHANGE_NAME, PUBLISHER_ROUTING_KEY,
                "topic"), 5, 5, TimeUnit.SECONDS);

        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        String consumerQueueName = channel.queueDeclare().getQueue();
        channel.queueBind(consumerQueueName, EXCHANGE_NAME, CONSUMER_ROUTING_KEY);

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                    byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println("-----------------");
                System.out.println("Received - " + message);
            }
        };

        channel.basicConsume(consumerQueueName, true, consumer);
    }
}

class MockSensorData {
    public volatile boolean isSuddenLossOfPressure = false;
    ArrayList<String> sensorDataList = new ArrayList<String>();
    public String[] changeTypes = { "increased", "decreased" };

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

    class SensorDataGenerator implements Runnable {
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

    class SensorDataPublisher extends PublisherHelper implements Runnable {
        public SensorDataPublisher(String publisherExchange, String publisherKey, String exchangeType) {
            super(publisherExchange, publisherKey, exchangeType);
        }

        @Override
        public void run() {
            try {
                System.out.println("Publishing " + sensorDataList.size() + " sensor data");
                for (int i = 0; i < sensorDataList.size(); i++) {
                    publish(sensorDataList.get(i));
                }
                sensorDataList.clear();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
