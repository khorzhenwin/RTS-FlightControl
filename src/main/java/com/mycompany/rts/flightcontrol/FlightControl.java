package com.mycompany.rts.flightcontrol;

import com.mycompany.rts.Processor.FlightControlProcessor;
import com.mycompany.rts.Publisher.LandingSignalPublisher;
import com.mycompany.rts.Publisher.ShutdownSignalPublisher;
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

        WorkerClass worker = new WorkerClass(
                flightControlProcessor,
                channel,
                EXCHANGE_NAME,
                SENSOR_PUBLISHER_ROUTING_KEY,
                ACTUATOR_PUBLISHER_ROUTING_KEY);

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                    byte[] body) throws IOException {
                String routingKey = envelope.getRoutingKey();
                String message = new String(body, "UTF-8");
                try {
                    Thread thread = new Thread();
                    if (routingKey.equals("sensor.data")) {
                        thread = new Thread(worker.new WorkerThread(true, message));
                    } else if (routingKey.equals("actuator.data")) {
                        thread = new Thread(worker.new WorkerThread(false, message));
                    }
                    thread.start();

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
                            flightControlProcessor.printLineChart("FCSProcessor",
                                    "FCS Processing Function");
                            flightControlProcessor.printDurationMetrics("FCS Processing Time", true);
                            flightControlProcessor.printDurationMetrics("FCS Processing Time", false);
                            System.exit(0);
                        }
                    }
                } catch (Exception e) {
                }

            }
        };

        channel.basicConsume(consumerQueueName, true, consumer);
    }

}

class WorkerClass {

    public FlightControlProcessor flightControlProcessor;
    public Channel channel;
    public String exchangeName;
    public String sensorKey;
    public String actuatorKey;

    public WorkerClass(FlightControlProcessor flightControlProcessor,
            Channel channel,
            String exchangeName,
            String sensorKey,
            String actuatorKey) {
        this.flightControlProcessor = flightControlProcessor;
        this.channel = channel;
        this.exchangeName = exchangeName;
        this.sensorKey = sensorKey;
        this.actuatorKey = actuatorKey;
    }

    public void processAndSendToActuator(String message)
            throws IOException, TimeoutException {
        System.out.println("Received sensor data: " + message);
        if (message.contains("landingMode")) {
            flightControlProcessor.isLandingMode = true;
            System.out.println("-------------------- Beginning Descent --------------------");
        } else {
            flightControlProcessor.withSensorData(message);
            String command = flightControlProcessor.getActuatorCommand(message);
            if (!command.equals("")) {
                channel.basicPublish(exchangeName,
                        actuatorKey, null,
                        command.getBytes("UTF-8"));
            }
            // signal actuators to deploy landing gear if altitude is less than 10000 feet
            if (flightControlProcessor.altitude < 2000
                    && flightControlProcessor.isLandingMode
                    && !flightControlProcessor.isLandingGearDeployed
                    && !flightControlProcessor.hasSentLandingGearDeploymentMessage) {
                flightControlProcessor.hasSentLandingGearDeploymentMessage = true;
                System.out.println("Altitude is less than 2000 feet. Sending signal to deploy landing gear");
                String x = "deploy [landingGear] to 1";
                channel.basicPublish(exchangeName,
                        actuatorKey, null,
                        x.getBytes("UTF-8"));
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

    public void processAndSendToSensor(String message)
            throws IOException, TimeoutException {
        if (flightControlProcessor.speed <= 10 && !flightControlProcessor.hasSentShutDownSpeedMessage) {
            String x = "shutdown speed generator";
            channel.basicPublish(exchangeName,
                    sensorKey, null,
                    x.getBytes("UTF-8"));
            flightControlProcessor.hasSentShutDownSpeedMessage = true;
            System.out.println("Shut down speed generator");
        }
        System.out.println("Received actuator data: " + message);
        flightControlProcessor.withActuatorData(message);
        // engineSpeed " + increase + " by " + value
        String[] messageParts = message.split(" ");
        String actuator = messageParts[0].trim();
        String correspondingSensor = flightControlProcessor.getCorresspondingSensorFromActuator(actuator);
        if (!actuator.equals("")) {
            String newSensorValue = flightControlProcessor.getSensorValue(correspondingSensor);
            String sensorNewValueFeedback = correspondingSensor + " sensor new reading : " + newSensorValue;
            channel.basicPublish(exchangeName,
                    sensorKey, null,
                    sensorNewValueFeedback.getBytes("UTF-8"));
        }
    }

    class WorkerThread implements Runnable {
        boolean isSensor;
        String message;

        public WorkerThread(boolean isSensor, String message) {
            this.isSensor = isSensor;
            this.message = message;
        }

        @Override
        public void run() {
            try {
                double start = System.currentTimeMillis();
                if (isSensor) {
                    processAndSendToActuator(message);
                } else {
                    processAndSendToSensor(message);
                }
                double end = System.currentTimeMillis();
                flightControlProcessor.addDuration(String.valueOf(end - start));
                flightControlProcessor.cycles++;

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
