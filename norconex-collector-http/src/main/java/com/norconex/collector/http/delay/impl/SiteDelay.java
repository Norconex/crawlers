package com.norconex.collector.http.delay.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
                    Sleeper.sleepNanos(Math.min(10, expectedDelayNanos));
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
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result
                    + (int) (lastHitEpochNanos ^ (lastHitEpochNanos >>> 32));
            result = prime * result + (sleeping ? 1231 : 1237);
            return result;
        }
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            SleepState other = (SleepState) obj;
            if (lastHitEpochNanos != other.lastHitEpochNanos)
                return false;
            if (sleeping != other.sleeping)
                return false;
            return true;
        }
    }
}
