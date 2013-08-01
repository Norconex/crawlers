package com.norconex.collector.http.delay.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.Sleeper;

public class SiteDelay extends AbstractDelay {


    private static final long serialVersionUID = -2927712101067189677L;

    private Map<String, SleepState> siteLastHitNanos =
            new ConcurrentHashMap<String, SleepState>();
    
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

    private class SleepState {
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
