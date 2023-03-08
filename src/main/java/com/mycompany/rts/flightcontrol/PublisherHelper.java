package com.mycompany.rts.flightcontrol;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class PublisherHelper {
    protected String publisherExchange;
    protected String publisherKey;
    protected String exchangeType;
    protected static ConnectionFactory cf = new ConnectionFactory();
    protected static Connection connection;
    protected static Channel channel;
    protected static String QUEUE_NAME;

    public PublisherHelper(String publisherExchange, String publisherKey, String exchangeType) {
        this.publisherExchange = publisherExchange;
        this.publisherKey = publisherKey;
        this.exchangeType = exchangeType;

        try {
            connection = cf.newConnection();
            channel = connection.createChannel();
            channel.exchangeDeclare(publisherExchange, exchangeType);
            QUEUE_NAME = channel.queueDeclare().getQueue();
            channel.queueBind(QUEUE_NAME, publisherExchange, publisherKey);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void publish(String msg) throws IOException, TimeoutException {
        channel.basicPublish(publisherExchange, publisherKey, false, null, msg.getBytes());
        System.out.println("Command Sent - " + msg);
    }
}

class LandingSignalPublisher extends PublisherHelper implements Runnable {
    String x;

    public LandingSignalPublisher(String publisherExchange, String publisherKey,
            String exchangeType, String x) {
        super(publisherExchange, publisherKey, exchangeType);
        this.x = x;
    }

    @Override
    public void run() {
        try {
            publish("landingMode initiated for " + x);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ShutdownSignalPublisher extends PublisherHelper implements Runnable {
    String x;

    public ShutdownSignalPublisher(String publisherExchange, String publisherKey,
            String exchangeType, String x) {
        super(publisherExchange, publisherKey, exchangeType);
        this.x = x;
    }

    @Override
    public void run() {
        try {
            publish("shutdownMode initiated for " + x);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}