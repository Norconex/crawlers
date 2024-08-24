/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.crawler.core.services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Duration;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.commons.lang.time.DurationUnit;
import com.norconex.crawler.core.services.monitor.CrawlerMonitor;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs useful information about the crawler execution progress.
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class CrawlerProgressLogger {

    private final StopWatch stopWatch = new StopWatch();

    private Duration minLoggingInterval;
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
    public CrawlerProgressLogger(
            CrawlerMonitor monitor, Duration minLoggingInterval) {
        this.monitor = monitor;
        if (minLoggingInterval != null && minLoggingInterval.getSeconds() < 0) {
            this.minLoggingInterval = Duration.ofSeconds(1);
        } else {
            this.minLoggingInterval = minLoggingInterval;
        }
    }

    public void startTracking() {
        prevProcessedCount = monitor.getProcessedCount();
        prevQueuedCount = monitor.getQueuedCount();
        stopWatch.reset();
        stopWatch.start();
    }
    public void stopTracking() {
        if (!stopWatch.isStopped()) {
            stopWatch.stop();
        }
    }

    // only log if logging was requested and enough time has elapsed.
    public void logProgress() {
        // If not logging, return right away
        if (minLoggingInterval != null && LOG.isInfoEnabled()) {
            doLogProgress();
        }
    }

    public String getExecutionSummary() {
        var elapsed = stopWatch.getTime();
        var processedCount = monitor.getProcessedCount();
        var b = new StringBuilder()
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
        var elapsed = stopWatch.getTime();
        if (elapsed < prevElapsed + minLoggingInterval.toMillis()) {
            return;
        }

        // OK, log it
        var processedCount = monitor.getProcessedCount();
        var queuedCount = monitor.getQueuedCount();

        var msg = infoMessage(elapsed, processedCount, queuedCount);
        if (LOG.isDebugEnabled()) {
            // if debugging, compute and show more stats
            LOG.info("{}{}", msg,
                    debugMessage(elapsed, processedCount, queuedCount));
        } else {
            LOG.info(msg);
        }
        prevProcessedCount = processedCount;
        prevQueuedCount = queuedCount;
        prevElapsed = elapsed;
    }

    private String infoMessage(
            long elapsed, long processedCount, long queuedCount) {
        var processedDelta = plusMinus(processedCount - prevProcessedCount);
        var queuedDelta = plusMinus(queuedCount - prevQueuedCount);
        var elapsedTime = durationFormatter.format(stopWatch.getTime());
        var throughput = divideDownStr(
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
        var totalSoFar = processedCount + queuedCount;
        var progress = divideDownStr(processedCount * 100, totalSoFar, 2);
        var remaining = durationFormatter.format(divideDown(
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
}
