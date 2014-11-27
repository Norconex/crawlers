/* Copyright 2010-2014 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.collector.http.delay.impl;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.Sleeper;

/**
 * Convenience class to encapsulate various delay strategies.
 * @author Pascal Essiembre
 */
public abstract class AbstractDelay {

    private static final Logger LOG = LogManager.getLogger(AbstractDelay.class);

    protected static final int TINY_SLEEP_MS = 10;
    private static final float THOUSAND_NANOS = 1000f;
    
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
                        + ((float) timeToSleepNanos * THOUSAND_NANOS)
                        + " seconds.");
            }
            Sleeper.sleepNanos(timeToSleepNanos);
        }
        // Ensure time has changed
        Sleeper.sleepNanos(1);
    }
}
