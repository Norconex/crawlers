/* Copyright 2010-2016 Norconex Inc.
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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;

public class SiteDelay extends AbstractDelay {

    private Map<String, SleepState> siteLastHitNanos = 
            new ConcurrentHashMap<>();
    
    @Override
    public void delay(long expectedDelayNanos, String url) {
        if (expectedDelayNanos <= 0) {
            return;
        }
        String site = StringUtils.lowerCase(
                url.replaceFirst("(.*?//.*?)(/.*)|$]", "$1"));
        SleepState sleepState = null;
        try {
            synchronized (siteLastHitNanos) {
                sleepState = siteLastHitNanos.get(site);
                if (sleepState == null) {
                    siteLastHitNanos.put(site, new SleepState());
                    return;
                }
                while (sleepState.sleeping) {
                    Sleeper.sleepNanos(Math.min(
                            TINY_SLEEP_MS, expectedDelayNanos));
                }
                sleepState.sleeping = true;
            }
            delay(expectedDelayNanos, sleepState.lastHitEpochNanos);
            sleepState.lastHitEpochNanos = System.nanoTime();
        } finally {
            if (sleepState != null) {
                sleepState.sleeping = false;
            }
        }
    }

    private static class SleepState {
        private long lastHitEpochNanos = System.nanoTime();
        private boolean sleeping;
        @Override
        public boolean equals(final Object other) {
            if (!(other instanceof SleepState)) {
                return false;
            }
            SleepState castOther = (SleepState) other;
            return new EqualsBuilder()
                    .append(lastHitEpochNanos, castOther.lastHitEpochNanos)
                    .append(sleeping, castOther.sleeping).isEquals();
        }
        @Override
        public int hashCode() {
            return new HashCodeBuilder().append(lastHitEpochNanos)
                    .append(sleeping).toHashCode();
        }
    }
}
