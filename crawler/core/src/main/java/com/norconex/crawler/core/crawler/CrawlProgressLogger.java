/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.core.monitor.CrawlerMonitor;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.commons.lang.time.DurationUnit;

/**
 * Logs useful information about the crawler execution progress.
 */
class CrawlProgressLogger {

    private static final Logger LOG =
            LoggerFactory.getLogger(CrawlProgressLogger.class);

    private final StopWatch stopWatch = new StopWatch();

    private final long minLoggingInterval;
    private final CrawlerMonitor monitor;

    // Just so we can show the delta between each log entry
    private long prevProcessedCount;
    private long prevQueuedCount;
    private long prevElapsed;

    private final DurationFormatter durationFormatter = new DurationFormatter()
            .withOuterLastSeparator(" and ")
            .withOuterSeparator(", ")
            .withUnitPrecision(2)
            .withLowestUnit(DurationUnit.SECOND);
    private final NumberFormat intFormatter = NumberFormat.getIntegerInstance();

    // Minimum 1 second
    CrawlProgressLogger(CrawlerMonitor monitor, long minLoggingInterval) {
        super();
        this.monitor = monitor;
        this.prevProcessedCount = monitor.getProcessedCount();
        this.prevQueuedCount = monitor.getQueuedCount();
        this.minLoggingInterval = Math.max(minLoggingInterval, 1000);
    }

    void startTracking() {
        stopWatch.start();
    }
    void stopTracking() {
        stopWatch.stop();
    }

    // only log if logging was requested and enough time has elapsed.
    void logProgress() {
        // If not logging, return right away
        if (LOG.isInfoEnabled()) {
            doLogProgress();
        }
    }

    String getExecutionSummary() {
        long elapsed = stopWatch.getTime();
        long processedCount = monitor.getProcessedCount();
        StringBuilder b = new StringBuilder()
                .append("\nTotal processed:   ")
                .append(processedCount)
                .append("\nSince (re)start:")
                .append("\n  Crawl duration:  ")
                .append(durationFormatter.format(elapsed))
                .append("\n  Avg. throughput: ")
                .append(divideDownStr(processedCount * 1000, elapsed, 1))
                .append(" processed/seconds")
                .append("\n  Event counts:");
        monitor.getEventCounts(). entrySet().stream().forEach(en -> b
                .append("\n    ")
                .append(StringUtils.rightPad(en.getKey() + ": ", 27))
                .append(intFormatter.format(en.getValue())));
        return b.toString();
    }

    synchronized void doLogProgress() {

        // If not enough time has elapsed, return
        long elapsed = stopWatch.getTime();
        if (elapsed < prevElapsed + minLoggingInterval) {
            return;
        }

        // OK, log it
        long processedCount = monitor.getProcessedCount();
        long queuedCount = monitor.getQueuedCount();

        String msg = infoMessage(elapsed, processedCount, queuedCount);
        if (LOG.isDebugEnabled()) {
            // if debugging, compute and show more stats
            LOG.info("{}{}", msg,
                    debugMessage(elapsed, processedCount, queuedCount));
        } else {
            LOG.info(msg);
        }
        this.prevProcessedCount = processedCount;
        this.prevQueuedCount = queuedCount;
        this.prevElapsed = elapsed;
    }

    private String infoMessage(
            long elapsed, long processedCount, long queuedCount) {
        String processedDelta = plusMinus(processedCount - prevProcessedCount);
        String queuedDelta = plusMinus(queuedCount - prevQueuedCount);
        String elapsedTime = durationFormatter.format(stopWatch.getTime());
        String throughput = divideDownStr(
                (processedCount - prevProcessedCount) * 1000,
                elapsed - prevElapsed,
                1);
        return String.format("%s(%s) processed "
                + "| %s(%s) queued | %s processed/sec | %s elapsed",
                processedCount,
                processedDelta,
                queuedCount,
                queuedDelta,
                throughput,
                elapsedTime);
    }
    private String debugMessage(
            long elapsed, long processedCount, long queuedCount) {
        long totalSoFar = processedCount + queuedCount;
        String progress = divideDownStr(processedCount * 100, totalSoFar, 2);
        String remaining = durationFormatter.format(divideDown(
                elapsed * queuedCount,
                processedCount,
                0).longValueExact());
        return String.format(" | ≈%s%% complete | ≈%s remaining",
                progress, remaining);
    }

    private String plusMinus(long val) {
        return (val >= 0 ? "+" : "") + intFormatter.format(val);
    }
    private BigDecimal divideDown(long dividend, long divisor, int scale) {
        if (divisor == 0L) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(dividend)
                .divide(BigDecimal.valueOf(divisor), scale, RoundingMode.DOWN)
                .stripTrailingZeros();
    }
    private String divideDownStr(long dividend, long divisor, int scale) {
        return divideDown(dividend, divisor, scale).toPlainString();
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }


}
