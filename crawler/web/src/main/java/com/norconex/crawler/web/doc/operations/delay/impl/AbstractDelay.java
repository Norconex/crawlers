/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.delay.impl;

import java.util.concurrent.TimeUnit;

import com.norconex.commons.lang.Sleeper;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Convenience class to encapsulate various delay strategies.
 */
@EqualsAndHashCode
@ToString
@Slf4j
public abstract class AbstractDelay {

    protected static final int TINY_SLEEP_MS = 5;

    public abstract void delay(long expectedDelayMillis, String url);

    protected void delay(long expectedDelayMillis, long lastHitMillis) {
        // Targeted delay in nanoseconds
        if (expectedDelayMillis <= 0) {
            return;
        }

        // How much time since last hit?
        var elapsedTimeMillis = System.currentTimeMillis() - lastHitMillis;

        // Sleep until targeted delay if not already passed.
        if (elapsedTimeMillis < expectedDelayMillis) {
            var timeToSleepMillis = expectedDelayMillis - elapsedTimeMillis;
            if (LOG.isDebugEnabled()) {
                var secs = TimeUnit.MILLISECONDS.toSeconds(timeToSleepMillis);
                var millisRemains = (timeToSleepMillis
                        - TimeUnit.SECONDS.toMillis(secs));
                LOG.debug("Thread sleeping for {} sec. and {} millis.",
                        secs, millisRemains);
            }
            Sleeper.sleepMillis(timeToSleepMillis);
        }
        // Ensure time has changed
        Sleeper.sleepMillis(1);
    }
}
