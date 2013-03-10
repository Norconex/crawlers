package com.norconex.collector.http.crawler;

import java.util.concurrent.TimeUnit;

import com.norconex.collector.http.robot.RobotsTxt;
import com.norconex.commons.lang.Sleeper;

public class DelayBlocker {

    private final long crawlerDelayNanos;
    private long lastTimestampNanos = -1;

    public DelayBlocker(long crawlerDelayMilis) {
        super();
        this.crawlerDelayNanos = 
                TimeUnit.MILLISECONDS.toNanos(crawlerDelayMilis);
    }
    
    synchronized public void wait(RobotsTxt robotsTxt, String url) {
        if (lastTimestampNanos == -1) {
            lastTimestampNanos = System.nanoTime();
            return;
        }
        long delayNanos = crawlerDelayNanos;
        if (robotsTxt != null 
                && robotsTxt.getCrawlDelay() >=0) {
            delayNanos = TimeUnit.MILLISECONDS.toNanos(
                    (long)(robotsTxt.getCrawlDelay() * 1000.0));
        }
        long elapsedNanoTime = System.nanoTime() - lastTimestampNanos;
        if (elapsedNanoTime < delayNanos) {
            Sleeper.sleepNanos(delayNanos - elapsedNanoTime);
        }
        lastTimestampNanos = System.nanoTime();
        Sleeper.sleepNanos(1);
    }
    
}
