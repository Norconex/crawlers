/* Copyright 2024-2025 Norconex Inc.
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

import static com.norconex.crawler.core.util.ExceptionSwallower.swallow;
import static java.util.Optional.ofNullable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.commons.collections4.CollectionUtils;

import com.norconex.crawler.core.cluster.pipeline.PipelineProgress;
import com.norconex.crawler.core.cluster.pipeline.PipelineProgressJMX;
import com.norconex.crawler.core.cluster.pipeline.PipelineResult;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.crawl.pipeline.CrawlPipelineFactory;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.metrics.CrawlerMetricsJMX;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlState;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.ExceptionSwallower;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CrawlCommand implements Command {

    public static final String SYS_PROP_ENABLE_JMX = "enableJMX";
    private final AtomicBoolean pendingLoggerStopped = new AtomicBoolean();
    private final CrawlPipelineFactory pipelineFactory;

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();

        pendingLoggerStopped.set(false); // just in case
        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            CrawlerMetricsJMX.register(ctx);
            LOG.info("JMX support enabled.");
        } else {
            LOG.info("JMX support disabled. To enable, set -DenableJMX=true "
                    + "system property as a JVM argument.");
        }

        Thread.currentThread().setName(ctx.getId() + "/CRAWL");
        session.fire(CrawlerEvent.CRAWLER_CRAWL_BEGIN, session);

        //XXX START TEST

        if (CollectionUtils.isNotEmpty(ctx.getBootstrappers())) {
            session.oncePerRun("crawl-bootstrappers", () -> {
                LOG.info("Running bootstrappers...");
                //TODO XXX rename getCrawlBootstrappers() or crawlRunBootstrappers
                ctx.getBootstrappers().forEach(boot -> boot.bootstrap(session));
            });

        }
        //XXX END TEST

        // Start monitoring for stop signals (crawl nodes only)
        session.getCluster().startStopMonitoring();

        // Build pipeline once so we can pass its id to the logger supplier
        var pipeline = pipelineFactory.create(session);

        // Register PipelineProgress MXBean on every node when JMX is enabled
        var pipelineJmxRegistered = false;
        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            try {
                PipelineProgressJMX.register(
                        ctx,
                        session.getCluster().getPipelineManager(),
                        pipeline.getId());
                pipelineJmxRegistered = true;
            } catch (Exception e) {
                LOG.warn("Could not register PipelineProgress MXBean: {}",
                        e.toString());
            }
        }

        // Start coordinator-only progress logger with pipeline progress
        // supplier
        // TODO this likely does not survive a change of coordinator???
        final CrawlProgressLogger logger;
        if (session.getCluster().getLocalNode().isCoordinator()) {
            Supplier<PipelineProgress> supplier = () -> {
                try {
                    return session.getCluster().getPipelineManager()
                            .getPipelineProgress(pipeline.getId());
                } catch (Exception e) {
                    return null; // logger is resilient to nulls
                }
            };
            logger = new CrawlProgressLogger(ctx, supplier);
            CompletableFuture.runAsync(logger::start);
        } else {
            logger = null;
        }

        CrawlState finalState = null;
        try {
            var pipeFuture = session.getCluster().getPipelineManager()
                    .executePipeline(pipeline);

            // Max duration is already handled in CrawlProcessStep, here we
            // have it again just as a safeguard, but we pad to give time to
            // CrawlProcessStep to finish normally after timeout.
            var maxDuration = ctx.getCrawlConfig().getMaxCrawlDuration();
            var result = (maxDuration != null && maxDuration.toMillis() > 0)
                    ? ConcurrentUtil.get(pipeFuture, 5, TimeUnit.MINUTES)
                    : ConcurrentUtil.get(pipeFuture);

            finalState = session.oncePerRunAndGet("final-status-task",
                    () -> storeFinalCrawlState(session, result));
        } catch (Exception e) {
            finalState = handleException(e, session, logger);
        }

        session.fire(CrawlerEvent.CRAWLER_CRAWL_END, session);
        LOG.info("Crawler terminated with state: {}", finalState);

        if (Boolean.getBoolean(SYS_PROP_ENABLE_JMX)) {
            LOG.info("Unregistering JMX crawler MBeans.");
            swallow(() -> CrawlerMetricsJMX.unregister(ctx));
            if (pipelineJmxRegistered) {
                swallow(() -> PipelineProgressJMX.unregister(ctx));
            }
        }

        // Schedule logger to be closed AFTER session cleanup (which fires
        // committer close events). This ensures the logger captures all
        // events before stopping.
        if (logger != null) {
            session.setPostCloseCleanup(() -> closeLogger(logger));
        }
    }

    private CrawlState handleException(
            Exception e, CrawlSession session, CrawlProgressLogger logger) {
        if (e instanceof CompletionException ce
                && ce.getCause() instanceof InterruptedException) {
            LOG.warn("Crawler interrupted, terminating gracefully.");
            Thread.currentThread().interrupt(); // restore flag
            session.updateCrawlState(CrawlState.STOPPED);
            closeLogger(logger);
            return CrawlState.STOPPED;
        }
        if (e instanceof java.util.concurrent.CancellationException) {
            LOG.info("Crawler cancelled due to stop signal, "
                    + "terminating gracefully.");
            session.updateCrawlState(CrawlState.STOPPED);
            closeLogger(logger);
            return CrawlState.STOPPED;
        }
        if (e instanceof CompletionException ce
                && ce.getCause() instanceof java.util.concurrent.CancellationException) {
            LOG.info("Crawler cancelled due to stop signal (wrapped), "
                    + "terminating gracefully.");
            session.updateCrawlState(CrawlState.STOPPED);
            closeLogger(logger);
            return CrawlState.STOPPED;
        }
        LOG.error("Crawler execution failed.", e);
        if (session.getCluster().getLocalNode().isCoordinator()) {
            // Attempt to stop logger even on failure
            closeLogger(logger);
        }
        session.updateCrawlState(CrawlState.FAILED);
        return CrawlState.FAILED;
    }

    private void closeLogger(CrawlProgressLogger logger) {
        ExceptionSwallower.swallowQuietly(() -> {
            if (logger != null) {
                logger.stop();
            }
        });
    }

    private CrawlState storeFinalCrawlState(
            CrawlSession session, PipelineResult result) {
        var pipeStatus = ofNullable(result).map(PipelineResult::getStatus)
                .orElse(PipelineStatus.FAILED);
        LOG.info("Crawl pipeline status: {}", pipeStatus);

        // Log diagnostic information for non-success statuses
        if (pipeStatus != PipelineStatus.COMPLETED) {
            LOG.warn("Pipeline did not complete successfully. Status: {}, "
                    + "Result: {}",
                    pipeStatus, result);
            if (pipeStatus == PipelineStatus.EXPIRED) {
                LOG.error("""
                    Pipeline EXPIRED - workers failed to report status \
                    within timeout. Check for worker thread issues, \
                    cluster connectivity problems, or deadlocks.""");
            }
        }

        var state = switch (pipeStatus) {
            case FAILED, EXPIRED, STOPPING, PENDING, RUNNING ->
                    CrawlState.FAILED;
            case COMPLETED -> CrawlState.COMPLETED;
            case STOPPED -> CrawlState.STOPPED;
            default -> throw new IllegalArgumentException(
                    "Unexpected value: " + pipeStatus);
        };
        session.updateCrawlState(state);
        return state;
    }
}
