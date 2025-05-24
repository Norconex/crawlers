/* Copyright 2021-2025 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Duration;
import java.util.Comparator;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.StopWatch;

import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.commons.lang.time.DurationUnit;
import com.norconex.crawler.core.metrics.CrawlerMetrics;
import com.norconex.crawler.core.session.CrawlContext;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Logs useful information about the crawler execution progress.
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class CrawlProgressLogger {

    private final StopWatch stopWatch = new StopWatch();

    private Duration minLoggingInterval;
    private final CrawlerMetrics monitor;

    // Just so we can show the delta between each log entry
    private long prevProcessedCount;
    private long prevQueuedCount;
    private long prevElapsed;

    private ScheduledExecutorService execService;
    private volatile boolean stopTrackingRequested;

    private final DurationFormatter durationFormatter = new DurationFormatter()
            .withOuterLastSeparator(" and ")
            .withOuterSeparator(", ")
            .withUnitPrecision(2)
            .withLowestUnit(DurationUnit.SECOND);
    private final NumberFormat intFormatter = NumberFormat.getIntegerInstance();

    // Minimum 1 second
    public CrawlProgressLogger(CrawlContext ctx) {
        monitor = ctx.getMetrics();
        var minInterval = ctx.getCrawlConfig().getMinProgressLoggingInterval();
        if (minInterval == null || minInterval.getSeconds() < 1) {
            minLoggingInterval = null;
        } else {
            minLoggingInterval = minInterval;
        }
    }

    public Void start() {
        stopTrackingRequested = false;
        if (minLoggingInterval != null && LOG.isInfoEnabled()) {
            execService = Executors.newSingleThreadScheduledExecutor();
            execService.scheduleWithFixedDelay(() -> {
                if (stopTrackingRequested) {
                    return;
                }
                logProgress();
            }, 0, minLoggingInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
        stopWatch.reset();
        stopWatch.start();
        prevProcessedCount = monitor.getProcessedCount();
        prevQueuedCount = monitor.getQueuedCount();

        while (!stopTrackingRequested) {
            try {
                Thread.sleep(100); // Small delay to avoid busy-waiting
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt
                break;
            }
        }

        return null;
    }

    public synchronized void stop() {
        if (stopTrackingRequested) {
            return;
        }
        if (!stopWatch.isStopped()) {
            stopWatch.stop();
        }
        if (execService != null) {
            try {
                stopTrackingRequested = true;
                execService.shutdown();
            } finally {
                execService = null;
            }
        }
        monitor.flush();
        LOG.info("Execution Summary:{}", getExecutionSummary());
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
                .append("\n  Event counts (incl. resumed):");
        monitor.getEventCounts()
                .entrySet()
                .stream()
                .sorted(Comparator.comparing(Entry<String, Long>::getKey))
                .forEach(en -> b.append("\n    ")
                        .append(StringUtils.rightPad(en.getKey() + ": ", 32))
                        .append(intFormatter.format(en.getValue())));
        return b.toString();
    }

    synchronized void logProgress() {

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
            LOG.info(
                    "{}{}", msg,
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
        return String.format(
                "%s(%s) processed "
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
        var remaining = durationFormatter.format(
                divideDown(
                        elapsed * queuedCount,
                        processedCount,
                        0).longValueExact());
        return String.format(
                " | ≈%s%% complete | ≈%s remaining",
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
