package com.mycompany.rts.flightcontrol;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;

public class Actuators {
    private static final String EXCHANGE_NAME = "flight_control_direct";
    private static final String EXCHANGE_TYPE = "direct";
    private static final String CONSUMER_ROUTING_KEY = "actuator_update";
    private static final String PUBLISHER_ROUTING_KEY = "actuator_data";
    private static final String ACTUATOR_CONSUMER_QUEUE_NAME = "fcs-to-actuator";
    private static final String ACTUATOR_PUBLISHER_QUEUE_NAME = "actuator-to-fcs";

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory factory = new ConnectionFactory();
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();
        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
        channel.queueDeclare(ACTUATOR_CONSUMER_QUEUE_NAME, false, false, false, null);
        channel.queueDeclare(ACTUATOR_PUBLISHER_QUEUE_NAME, false, false, false, null);
        channel.queueBind(ACTUATOR_CONSUMER_QUEUE_NAME, EXCHANGE_NAME, CONSUMER_ROUTING_KEY);
        channel.queueBind(ACTUATOR_PUBLISHER_QUEUE_NAME, EXCHANGE_NAME, PUBLISHER_ROUTING_KEY);

        Consumer consumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties,
                    byte[] body) throws IOException {
                String message = new String(body, "UTF-8");
                System.out.println("Received FCS command: " + message);
                // this should only run once
                if (message.contains("shutdownMode")) {
                    try {
                        channel.close();
                        connection.close();
                        System.out.println("Connection closed");
                    } catch (TimeoutException e) {
                    }
                } else {
                    ArrayList<String> acknowledgementMessages = getAcknowledgementMessage(message);
                    for (String acknowledgementMessage : acknowledgementMessages) {
                        channel.basicPublish(EXCHANGE_NAME,
                                PUBLISHER_ROUTING_KEY, null,
                                acknowledgementMessage.getBytes("UTF-8"));
                        System.out.println("Sent actuator data: " + acknowledgementMessage);
                    }
                }
                System.out.println();
            }
        };

        channel.basicConsume(ACTUATOR_CONSUMER_QUEUE_NAME, true, consumer);
    }

    public static ArrayList<String> getAcknowledgementMessage(String message) {
        // format "increase/decrease/open/close [sensor1,sensor2,sensor3] by 10"
        ArrayList<String> acknowledgementMessages = new ArrayList<String>();
        String[] tokens = message.split(" ");
        String[] sensors = tokens[1].trim().replaceAll("[\\[\\]]", "").split(",");
        for (String sensor : sensors) {
            sensor = sensor.trim();
            if (sensor.equals("vents")) {
                acknowledgementMessages.add(sensor + " " + tokens[0] + " for 10 seconds");
                continue;
            } else if (sensor.equals("oxygenMask")) {
                acknowledgementMessages.add("oxygenMask deployed x 1");
                continue;
            } else if (sensor.equals("landingGear")) {
                acknowledgementMessages.add("landingGear deployed x 1");
                continue;
            }
            acknowledgementMessages.add(sensor + " " + tokens[0] + " by " + tokens[3]);
        }

        return acknowledgementMessages;
    }

}
