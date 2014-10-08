package com.norconex.collector.http.delay.impl;

import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.commons.lang.Sleeper;

/**
 * It is assumed there will be one instance of this class per crawler defined.
 * @author Pascal Essiembre
 */
public class CrawlerDelay extends AbstractDelay {

    private MutableLong lastHitEpochNanos = new MutableLong(-1);
    private boolean sleeping = false;

    public void delay(long expectedDelayNanos, String url) {
        if (expectedDelayNanos <= 0) {
            return;
        }
        try {
            synchronized (lastHitEpochNanos) {
                while (sleeping) {
                    Sleeper.sleepNanos(Math.min(
                            TINY_SLEEP_MS, expectedDelayNanos));
                }
                sleeping = true;
            }
            delay(expectedDelayNanos, lastHitEpochNanos.longValue());
            lastHitEpochNanos.setValue(System.nanoTime());
        } finally {
            sleeping = false;
        }
    }
}
