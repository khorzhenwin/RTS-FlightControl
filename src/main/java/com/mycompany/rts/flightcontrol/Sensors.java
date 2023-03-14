package com.mycompany.rts.flightcontrol;

import com.mycompany.rts.Processor.MockSensorData;
import java.io.IOException;
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

    protected static final String EXCHANGE_NAME = "flight_control_direct";
    protected static final String EXCHANGE_TYPE = "direct";
    protected static final String PUBLISHER_ROUTING_KEY = "sensor_data";
    protected static final String CONSUMER_ROUTING_KEY = "sensor_update";
    protected static final String SENSOR_CONSUMER_QUEUE_NAME = "fcs-to-sensor";
    protected static final String SENSOR_PUBLISHER_QUEUE_NAME = "sensor-to-fcs";

    public static void main(String[] args) throws IOException, TimeoutException {
        // ------------------------------- PRODUCERS -------------------------------
        // "altitude", "cabinPressure", "speed", "rain"
        MockSensorData mockSensorData = new MockSensorData();
        String[] sensorTypes = { "altitude", "speed", "cabinPressure", "rain" };
        ScheduledExecutorService mockDataGeneratorExecutor = Executors.newScheduledThreadPool(4);
        ScheduledExecutorService publisherExecutor = Executors.newScheduledThreadPool(1);
        ScheduledExecutorService landingAltitudeDataExecutor = Executors.newScheduledThreadPool(1);
        ScheduledExecutorService landingSpeedDataExecutor = Executors.newScheduledThreadPool(1);

        // ------------------------------- CONSUMERS -------------------------------
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
        channel.queueDeclare(SENSOR_CONSUMER_QUEUE_NAME, false, false, false, null);
        channel.queueDeclare(SENSOR_PUBLISHER_QUEUE_NAME, false, false, false, null);
        channel.queueBind(SENSOR_CONSUMER_QUEUE_NAME, EXCHANGE_NAME, CONSUMER_ROUTING_KEY);
        channel.queueBind(SENSOR_PUBLISHER_QUEUE_NAME, EXCHANGE_NAME, PUBLISHER_ROUTING_KEY);

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                    byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println("########### Received - " + message);
                checkFlightModeAndProcess(message, mockSensorData);

                if (message.contains("shutdown speed generator")) {
                    landingSpeedDataExecutor.shutdownNow();
                } else if (message.contains("landingMode")) {
                    mockDataGeneratorExecutor.shutdownNow();
                    landingAltitudeDataExecutor.scheduleAtFixedRate(mockSensorData.new SensorDataGenerator("altitude"),
                            4, 4, TimeUnit.SECONDS);
                    landingSpeedDataExecutor.scheduleAtFixedRate(mockSensorData.new SensorDataGenerator("speed"),
                            4, 4, TimeUnit.SECONDS);
                }
            }
        };

        channel.basicConsume(SENSOR_CONSUMER_QUEUE_NAME, true, consumer);

        for (String sensorType : sensorTypes) {
            mockDataGeneratorExecutor.scheduleAtFixedRate(mockSensorData.new SensorDataGenerator(sensorType),
                    4, 4, TimeUnit.SECONDS);
        }
        publisherExecutor.scheduleAtFixedRate(
                mockSensorData.new SensorDataPublisher(EXCHANGE_NAME, PUBLISHER_ROUTING_KEY, EXCHANGE_TYPE),
                5, 5, TimeUnit.SECONDS);
    }

    public static void checkFlightModeAndProcess(String message, MockSensorData mockSensorData) {
        if (message.contains("sensor new reading")) {
            mockSensorData.totalConsumed++;
            if (mockSensorData.startTime != 0) {
                mockSensorData.cycles++;
                mockSensorData.endTime = System.currentTimeMillis();
                mockSensorData.addDuration(mockSensorData.getTimeDifference());
                mockSensorData.startTime = 0;
            }
        } else if (message.contains("shutdownMode")) {
            System.out.println("Connection closed");
            mockSensorData.printLineChart("feedbackLoop", "Feedback Loop Life Cycle");
            mockSensorData.printDurationMetrics("Feedback Loop Life Cycle", true);
            mockSensorData.printDurationMetrics("Feedback Loop Life Cycle", false);
            mockSensorData.printThroughputMetrics();
            System.exit(0);
        } else if (message.contains("landingMode") && !mockSensorData.isLandingMode) {
            System.out.println("-------------------- Landing mode activated --------------------");
            mockSensorData.isLandingMode = true;
            mockSensorData.changeTypes = new String[] { "decreased" };
            mockSensorData.sensorDataList.clear();
            mockSensorData.sensorDataList.add("landingMode acknowledged");
        }
    }
}
