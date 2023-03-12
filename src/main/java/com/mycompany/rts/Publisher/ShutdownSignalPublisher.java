package com.mycompany.rts.Publisher;

import com.mycompany.rts.Helper.PublisherHelper;

public class ShutdownSignalPublisher extends PublisherHelper implements Runnable {
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