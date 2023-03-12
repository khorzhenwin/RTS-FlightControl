package com.mycompany.rts.Publisher;

import com.mycompany.rts.Helper.PublisherHelper;

public class LandingSignalPublisher extends PublisherHelper implements Runnable {
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
