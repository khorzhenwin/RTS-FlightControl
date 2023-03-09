package com.mycompany.rts.flightcontrol;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class FlightControl {
    private static final String EXCHANGE_NAME = "flight_control";
    private static final String EXCHANGE_TYPE = "topic";
    private static final String CONSUMER_ROUTING_KEY = "*.data";
    private static final String ACTUATOR_PUBLISHER_ROUTING_KEY = "actuator.update";
    private static final String SENSOR_PUBLISHER_ROUTING_KEY = "sensor.update";

    public static void main(String[] args) throws IOException, TimeoutException {
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        FlightControlProcessor flightControlProcessor = new FlightControlProcessor();
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);

        executor.scheduleAtFixedRate(flightControlProcessor.new FlightControlMonitor(), 0, 5, TimeUnit.SECONDS);
        // publish landing signal after 30 seconds only one time
        executor.schedule(
                new LandingSignalPublisher(EXCHANGE_NAME, SENSOR_PUBLISHER_ROUTING_KEY, EXCHANGE_TYPE, "sensor"),
                30, TimeUnit.SECONDS);
        executor.schedule(
                new LandingSignalPublisher(EXCHANGE_NAME, ACTUATOR_PUBLISHER_ROUTING_KEY, EXCHANGE_TYPE, "actuator"),
                30, TimeUnit.SECONDS);

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
                try {
                    if (routingKey.equals("sensor.data")) {
                        processAndSendToActuator(flightControlProcessor, channel, message);
                    } else if (routingKey.equals("actuator.data")) {
                        processAndSendToSensor(flightControlProcessor, channel, message);
                    }

                    if (flightControlProcessor.hasLanded) {
                        Thread shutdownSensor = new Thread(
                                new ShutdownSignalPublisher(EXCHANGE_NAME, SENSOR_PUBLISHER_ROUTING_KEY,
                                        EXCHANGE_TYPE, "sensors"));
                        Thread shutdownActuator = new Thread(
                                new ShutdownSignalPublisher(EXCHANGE_NAME, ACTUATOR_PUBLISHER_ROUTING_KEY,
                                        EXCHANGE_TYPE, "actuators"));
                        shutdownSensor.start();
                        shutdownActuator.start();

                        executor.shutdown();
                        channel.close();
                        connection.close();
                        if (!shutdownSensor.isAlive() && !shutdownActuator.isAlive()) {
                            System.exit(0);
                        }
                    }
                } catch (Exception e) {
                }
            }
        };

        channel.basicConsume(consumerQueueName, true, consumer);
    }

    public static void processAndSendToActuator(FlightControlProcessor flightControlProcessor, Channel channel,
            String message)
            throws IOException, TimeoutException {
        System.out.println("Received sensor data: " + message);
        if (message.contains("landingMode")) {
            flightControlProcessor.isLandingMode = true;
            System.out.println("-------------------- Beginning Descent --------------------");
        } else {
            flightControlProcessor.withSensorData(message);
            String command = flightControlProcessor.getActuatorCommand(message);
            if (!command.equals("")) {
                channel.basicPublish(EXCHANGE_NAME, ACTUATOR_PUBLISHER_ROUTING_KEY, null,
                        command.getBytes("UTF-8"));
            }
            // signal actuators to deploy landing gear if altitude is less than 10000 feet
            if (flightControlProcessor.altitude < 2000
                    && flightControlProcessor.isLandingMode
                    && !flightControlProcessor.isLandingGearDeployed
                    && !flightControlProcessor.hasSentLandingGearDeploymentMessage) {
                flightControlProcessor.hasSentLandingGearDeploymentMessage = true;
                System.out.println("Altitude is less than 2000 feet. Sending signal to deploy landing gear");
                channel.basicPublish(EXCHANGE_NAME, ACTUATOR_PUBLISHER_ROUTING_KEY, null,
                        "deploy [landingGear] to 1".getBytes("UTF-8"));
            } else if (flightControlProcessor.altitude < 1000 && flightControlProcessor.isLandingGearDeployed) {
                System.out.println("Reached optimum altitude to land");
                System.out.println("Landing ....");
                System.out.println("Landing ....");
                System.out.println("Landing ....");
                System.out.println("Plane has sucessfully landed");
                System.out.println("Shutting down all services ...");
                flightControlProcessor.hasLanded = true;
            }
        }
        System.out.println();
    }

    public static void processAndSendToSensor(FlightControlProcessor flightControlProcessor, Channel channel,
            String message)
            throws IOException, TimeoutException {
        if (flightControlProcessor.speed <= 10) {
            channel.basicPublish(EXCHANGE_NAME, SENSOR_PUBLISHER_ROUTING_KEY, null,
                    "shutdown speed generator".getBytes("UTF-8"));
        }
        System.out.println("Received actuator data: " + message);
        flightControlProcessor.withActuatorData(message);
        // engineSpeed " + increase + " by " + value
        String[] messageParts = message.split(" ");
        String correspondingSensor = flightControlProcessor
                .getCorresspondingSensorFromActuator(messageParts[0].trim());
        String newSensorValue = flightControlProcessor.getSensorValue(correspondingSensor);
        String sensorNewValueFeedback = correspondingSensor + " sensor new reading : " + newSensorValue;
        channel.basicPublish(EXCHANGE_NAME,
                SENSOR_PUBLISHER_ROUTING_KEY, null,
                sensorNewValueFeedback.getBytes("UTF-8"));
    }
}

class FlightControlProcessor implements FlightMode {
    public volatile boolean isLandingMode = false;
    public volatile boolean hasLanded = false;
    public volatile boolean hasSentLandingGearDeploymentMessage = false;
    // Sensor data
    public volatile int altitude = 30000; // normal range of 30000 feet
    public volatile int cabinPressure = 50; // percentage of max pressure 1-100
    public volatile int speed = 300; // km/h
    public volatile int rainfallMagnitude = 0; // percentage of max rainfall 0-100

    // Acutuator data
    public volatile int engineSpeed = 50; // percentage of max speed 1-100
    public volatile int tailFlapsAngle = 0; // degrees -90 to 90
    public volatile int wingFlapsAngle = 0; // degrees -90 to 90
    public volatile boolean isLandingGearDeployed = false;
    public volatile boolean isOxygenMaskDeployed = false;

    public synchronized void setIntValues(String sensorOrActuatorType, int value) {
        switch (sensorOrActuatorType) {
            case "altitude":
                altitude += value;
                altitude = (altitude < 500) ? 500 : altitude;
                break;
            case "cabinPressure":
                cabinPressure += value;
                if (cabinPressure > 100) {
                    cabinPressure = 100;
                } else if (cabinPressure < 0) {
                    cabinPressure = 0;
                }
                break;
            case "speed":
                speed += value;
                speed = (speed < 5) ? 5 : speed;
                break;
            case "rain":
                rainfallMagnitude += value;
                if (rainfallMagnitude > 100) {
                    rainfallMagnitude = 100;
                } else if (rainfallMagnitude < 0) {
                    rainfallMagnitude = 0;
                }
                break;
            case "engineSpeed":
                engineSpeed += value;
                engineSpeed = (engineSpeed < 0) ? 0 : engineSpeed;
                break;
            case "tailFlapsAngle":
                tailFlapsAngle += value;
                if (tailFlapsAngle > 90) {
                    tailFlapsAngle = 90;
                } else if (tailFlapsAngle < -90) {
                    tailFlapsAngle = -90;
                }
                break;
            case "wingFlapsAngle":
                wingFlapsAngle += value;
                if (wingFlapsAngle > 90) {
                    wingFlapsAngle = 90;
                } else if (wingFlapsAngle < -90) {
                    wingFlapsAngle = -90;
                }
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

    public synchronized void withActuatorData(String message) {
        // engineSpeed " + increase + " by " + value
        String[] messageParts = message.split(" ");
        String actuatorType = messageParts[0].trim();
        String changeType = messageParts[1].trim();
        int changeValue = (changeType.equals("increase")) ? Integer.parseInt(messageParts[3])
                : -Integer.parseInt(messageParts[3]);
        if (actuatorType.equals("vents")) {
            changeValue = (changeType.equals("open")) ? -10 : 10; // vents open = cabinPressure decrease
        }
        switch (actuatorType) {
            case "engineSpeed":
                setIntValues("engineSpeed", changeValue);
                // for every 10% increase in engineSpeed, change speed by 10 km/h
                System.out.println("speed value before change: " + speed + " km/h");
                setIntValues("speed", (changeValue / 5) * 10);
                System.out.println("speed value after change: " + speed + " km/h");
                break;
            case "tailFlapsAngle":
                setIntValues("tailFlapsAngle", changeValue);
                // for every 5 degree change in tailFlapsAngle, change altitude by 500 feet
                System.out.println("altitude value before change: " + altitude + " feet");
                setIntValues("altitude", (changeValue / 5) * 500);
                System.out.println("altitude value after change: " + altitude + " feet");
                break;
            case "wingFlapsAngle":
                setIntValues("wingFlapsAngle", changeValue);
                // for every 5 degree change in wingFlapsAngle, change altitude by 500 feet
                System.out.println("altitude value before change: " + altitude + " feet");
                setIntValues("altitude", (changeValue / 5) * 500);
                System.out.println("altitude value after change: " + altitude + " feet");
                break;
            case "vents":
                // change cabinPressure
                System.out.println("cabinPressure value before change: " + cabinPressure + " %");
                setIntValues("cabinPressure", changeValue);
                System.out.println("cabinPressure value after change: " + cabinPressure + " %");
                break;
            case "oxygenMask":
                isOxygenMaskDeployed = true;
                System.out.println("--------- OXYGEN MASK SUCCESSFULLY DEPLOYED ---------");
                System.out.println("Emergency repressuring cabin and closing vents");
                setIntValues("cabinPressure", 50);
                break;
            case "landingGear":
                isLandingGearDeployed = true;
                System.out.println("--------- LANDING GEAR SUCCESSFULLY DEPLOYED ---------");
                break;
            default:
                break;
        }
        System.out.println();
    }

    @Override
    public String getActuatorCommand(String message) {
        if (isLandingMode) {
            return getActuatorCommandInLandingMode(message);
        } else {
            return getActuatorCommandInCruisingMode(message);
        }
    }

    public String getActuatorCommandInCruisingMode(String message) {
        String[] messageParts = message.split(" ");// format eg "altitude increased 1000"
        String sensorType = messageParts[0].trim();
        String changeType = messageParts[1].trim();
        String commandChangeType = (changeType.equals("increased")) ? "decrease" : "increase";
        String finalCommand = "";

        switch (sensorType) {
            case "altitude":
                // for every 1000ft, lower engineSpeed by 5% & lower flaps by 5 degrees
                int changeActuatorValue = (Integer.parseInt(messageParts[2]) / 1000) * 5;
                finalCommand = String.format("%s [engineSpeed,tailFlapsAngle,wingFlapsAngle] by %s", commandChangeType,
                        changeActuatorValue);
                break;
            case "cabinPressure":
                // when cabinPressure deviate from initial value of 50 by 20%, open/close vents
                if (cabinPressure > 70) {
                    finalCommand = "open [vents]";
                } else if (cabinPressure < 30 && cabinPressure > 10) {
                    finalCommand = "close [vents]";
                } else if (cabinPressure < 10 && !isOxygenMaskDeployed) {
                    System.out.println("--------- EMERGENCY DEPLOYING OXYGEN MASK ---------");
                    System.out.println("--------- EMERGENCY LOWERING ALTITUDE ---------");
                    finalCommand = "decrease [engineSpeed,tailFlapsAngle,wingFlapsAngle,oxygenMask] by 50";
                }
                break;
            case "speed":
                finalCommand = (speed > 400) ? "decrease [engineSpeed] by 10"
                        : (speed < 200) ? "increase [engineSpeed] by 10"
                                : "";
                break;
            case "rain":
                if (rainfallMagnitude < 10) {
                    return "";
                }
                // lower engineSpeed by 2% when rainfallMagnitude increases every 10%
                int changeEngineSpeed = (rainfallMagnitude / 10) * -2;
                finalCommand = String.format("%s [engineSpeed] by %s", commandChangeType, changeEngineSpeed);
                break;
            default:
                break;
        }
        if (!finalCommand.equals("")) {
            System.out.println("Command sent for " + sensorType + ": " + finalCommand);
        }
        return finalCommand;
    }

    public String getActuatorCommandInLandingMode(String message) {
        String[] messageParts = message.split(" ");// format eg "altitude increased 1000"
        String sensorType = messageParts[0].trim();
        String commandChangeType = "decrease";
        String finalCommand = "";

        switch (sensorType) {
            case "altitude":
                // for every 1000ft, lower engineSpeed by 5% & lower flaps by 5 degrees
                int changeActuatorValue = (Integer.parseInt(messageParts[2]) / 1000) * 5;
                finalCommand = String.format("%s [engineSpeed,tailFlapsAngle,wingFlapsAngle] by %s", commandChangeType,
                        changeActuatorValue);
                finalCommand = (altitude == 500) ? "" : finalCommand;
                break;
            case "speed":
                finalCommand = (speed == 0) ? "" : "decrease [engineSpeed] by 5";
                break;
            default:
                break;
        }

        // return format -"decrease [engineSpeed,tailFlapsAngle,wingFlapsAngle] by 10"
        return finalCommand;
    }

    public String getSensorValue(String sensor) {
        switch (sensor) {
            case "altitude":
                return String.valueOf(altitude);
            case "cabinPressure":
                return String.valueOf(cabinPressure);
            case "speed":
                return String.valueOf(speed);
            case "rainfallMagnitude":
                return String.valueOf(rainfallMagnitude);
            default:
                return "";
        }
    }

    public String getCorresspondingSensorFromActuator(String actuator) {
        switch (actuator) {
            case "engineSpeed":
                return "speed";
            case "tailFlapsAngle":
                return "altitude";
            case "wingFlapsAngle":
                return "altitude";
            case "vents":
                return "cabinPressure";
            default:
                return "";
        }
    }

    class FlightControlMonitor implements Runnable {

        @Override
        public void run() {
            System.out.println("--------SENSOR VALUES--------");
            System.out.println("Altitude: " + altitude);
            System.out.println("Cabin Pressure: " + cabinPressure);
            System.out.println("Speed: " + speed);
            System.out.println("Rainfall Magnitude: " + rainfallMagnitude);
            System.out.println("--------ACTUATOR VALUES--------");
            System.out.println("Engine Speed: " + engineSpeed);
            System.out.println("Tail Flaps Angle: " + tailFlapsAngle);
            System.out.println("Wing Flaps Angle: " + wingFlapsAngle);
            System.out.println("Oxygen Mask Deployed: " + isOxygenMaskDeployed);
            System.out.println("Landing Gear Deployed: " + isLandingGearDeployed);
            System.out.println("----------------------------");
            System.out.println();

        }
    }

}
