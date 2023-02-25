package com.mycompany.rts.flightcontrol;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FlightControl {
    private static final String EXCHANGE_NAME = "flight_control";
    private static final String CONSUMER_ROUTING_KEY = "*.data";
    private static final String ACTUATOR_PUBLISHER_ROUTING_KEY = "actuator.update";
    private static final String SENSOR_PUBLISHER_ROUTING_KEY = "sensor.update";

    public static void main(String[] args) throws IOException, TimeoutException {
        FlightControlProcessor flightControlProcessor = new FlightControlProcessor();
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, "topic");

        // publish on a *.update queue
        // subscribe on a *.data queue
        String consumerQueueName = channel.queueDeclare().getQueue();
        channel.queueBind(consumerQueueName, EXCHANGE_NAME, CONSUMER_ROUTING_KEY);

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                    byte[] body) throws IOException {
                String routingKey = envelope.getRoutingKey();
                String message = new String(body, "UTF-8");
                if (routingKey.equals("sensor.data")) {
                    System.out.println("Received sensor data: " + message);
                    flightControlProcessor.withSensorData(message);
                    String command = flightControlProcessor.getActuatorCommand(message);
                    if (!command.equals("")) {
                        channel.basicPublish(EXCHANGE_NAME, ACTUATOR_PUBLISHER_ROUTING_KEY, null,
                                command.getBytes("UTF-8"));
                    }
                    System.out.println();
                } else if (routingKey.equals("actuator.data")) {
                    System.out.println("Received actuator data: " + message);
                }
            }
        };

        channel.basicConsume(consumerQueueName, true, consumer);
    }
}

class FlightControlProcessor {
    public volatile boolean isLandingMode = false;
    // Sensor data
    public volatile int altitude = 30000; // normal range of 30000 feet
    public volatile int cabinPressure = 50; // percentage of max pressure 1-100
    public volatile int speed = 300; // km/h
    public volatile int rainfallMagnitude = 0; // percentage of max rainfall 0-100

    // Acutuator data
    public volatile int engineSpeed = 50; // percentage of max speed 1-100
    public volatile int tailFlapsAngle = 0; // degrees -90 to 90
    public volatile int wingFlapsAngle = 0; // degrees -90 to 90
    public volatile boolean isVentsOpen = true;
    public volatile boolean isLandingGearDeployed = false;
    public volatile boolean isOxygenMaskDeployed = false;

    public void printPlaneState() {
        System.out.println("--------SENSOR VALUES--------");
        System.out.println("Altitude: " + altitude);
        System.out.println("Cabin Pressure: " + cabinPressure);
        System.out.println("Speed: " + speed);
        System.out.println("Rainfall Magnitude: " + rainfallMagnitude);
        System.out.println("--------ACTUATOR VALUES--------");
        System.out.println("Engine Speed: " + engineSpeed);
        System.out.println("Tail Flaps Angle: " + tailFlapsAngle);
        System.out.println("Wing Flaps Angle: " + wingFlapsAngle);
        System.out.println("Landing Gear Deployed: " + isLandingGearDeployed);
        System.out.println("Oxygen Mask Deployed: " + isOxygenMaskDeployed);
        System.out.println("----------------------------");
        System.out.println();
    }

    public synchronized void setIntValues(String sensorOrActuatorType, int value) {
        switch (sensorOrActuatorType) {
            case "altitude":
                altitude += value;
                break;
            case "cabinPressure":
                cabinPressure += value;
                if (cabinPressure > 100)
                    cabinPressure = 100;
                else if (cabinPressure < 0)
                    cabinPressure = 0;
                break;
            case "speed":
                speed += value;
                break;
            case "rain":
                rainfallMagnitude += value;
                if (rainfallMagnitude > 100)
                    rainfallMagnitude = 100;
                else if (rainfallMagnitude < 0)
                    rainfallMagnitude = 0;
                break;
            case "engineSpeed":
                engineSpeed += value;
                break;
            case "tailFlapsAngle":
                tailFlapsAngle += value;
                if (tailFlapsAngle > 90)
                    tailFlapsAngle = 90;
                else if (tailFlapsAngle < -90)
                    tailFlapsAngle = -90;
                break;
            case "wingFlapsAngle":
                wingFlapsAngle += value;
                if (wingFlapsAngle > 90)
                    wingFlapsAngle = 90;
                else if (wingFlapsAngle < -90)
                    wingFlapsAngle = -90;
                break;
            default:
                break;
        }
    }

    public synchronized void withSensorData(String message) {
        String[] messageParts = message.split(" ");// format eg "altitude increased 1000"
        String sensorType = messageParts[0].trim();
        String changeType = messageParts[1].trim();
        int changeValue = (changeType.equals("increased")) ? Integer.parseInt(messageParts[2])
                : -Integer.parseInt(messageParts[2]);
        setIntValues(sensorType, changeValue);
        System.out.println(sensorType + " reading has been " + changeType + " by " + changeValue);
    }

    public String getActuatorCommand(String message) {
        String[] messageParts = message.split(" ");// format eg "altitude increased 1000"
        String sensorType = messageParts[0].trim();
        String changeType = messageParts[1].trim();
        int changeValue = (changeType.equals("increased")) ? Integer.parseInt(messageParts[2])
                : -Integer.parseInt(messageParts[2]);
        String commandChangeType = (changeType.equals("increased")) ? "decrease" : "increase";
        String finalCommand = "";

        // altitude has a range to change by 3000
        // cabinPressure has a range to change by 10
        // speed has a range to change by 20
        // rainfallMagnitude has a range to change by 10
        switch (sensorType) {
            case "altitude":
                // for every 1000ft, lower engineSpeed by 5% & lower flaps by 5 degrees
                int changeActuatorValue = (changeValue / 1000) * -5;
                finalCommand = String.format("%s [engineSpeed,tailFlapsAngle,wingFlapsAngle] by %s", commandChangeType,
                        changeActuatorValue);
                System.out.println("Command sent to balance altitude: " + finalCommand);
                break;
            case "cabinPressure":
                // when cabinPressure deviate from initial value of 50 by 20%, open/close vents
                finalCommand = (cabinPressure > 70) ? "open [vents]" : (cabinPressure < 30) ? "close vents" : "";
                System.out.println("Command sent to balance cabin pressure: " + finalCommand);
                if (finalCommand.equals("")) {
                    return "";
                }
                break;
            case "speed":
                finalCommand = (speed > 400) ? "decrease [engineSpeed] by 10"
                        : (speed < 200) ? "increase [engineSpeed] by 10"
                                : "";
                if (finalCommand.equals("")) {
                    return "";
                }
                System.out.println("Command sent to balance speed: " + finalCommand);
                break;
            case "rain":
                if (rainfallMagnitude < 10) {
                    return "";
                }
                // lower engineSpeed by 2% when rainfallMagnitude increases every 10%
                int changeEngineSpeed = (rainfallMagnitude / 10) * -2;
                finalCommand = String.format("%s [engineSpeed] by %s", commandChangeType, changeEngineSpeed);
                System.out.println("Command sent due to increased rainfall: " + finalCommand);
                break;
            default:
                break;
        }
        return finalCommand;
    }

}
