package com.norconex.collector.http.delay.impl;

import java.io.Serializable;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.Sleeper;

/**
 * Convenience class to encapsulate various delay strategies.
 * @author Pascal Essiembre
 */
public abstract class AbstractDelay implements Serializable {

    private static final long serialVersionUID = 3203916955634153382L;
    private static final Logger LOG = LogManager.getLogger(AbstractDelay.class);

    public abstract void delay(long expectedDelayNanos, String url);
    
    protected void delay(long expectedDelayNanos, long lastHitNanos) {
        // Targeted delay in nanoseconds
        if (expectedDelayNanos <= 0) {
            return;
        }
        
        // How much time since last hit?
        long elapsedTimeNanos = System.nanoTime() - lastHitNanos;

        // Sleep until targeted delay if not already passed.
        if (elapsedTimeNanos < expectedDelayNanos) {
            long timeToSleepNanos = expectedDelayNanos - elapsedTimeNanos;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Thread " + Thread.currentThread().getName()
                        + " sleeping for "
                        + ((float) timeToSleepNanos * 1000f)
                        + " seconds.");
            }
            Sleeper.sleepNanos(timeToSleepNanos);
        }
        // Ensure time has changed
        Sleeper.sleepNanos(1);
    }

}
