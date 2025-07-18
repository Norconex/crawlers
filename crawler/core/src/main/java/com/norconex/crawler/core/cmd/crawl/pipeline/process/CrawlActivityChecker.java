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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.core.session.CrawlState;
import com.norconex.grid.core.compute.GridTaskBuilder;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Uses a few different criteria to find out if the crawler should still be
 * considered active. When applicable, will wait until that information
 * can be established.
 */
@RequiredArgsConstructor
@Slf4j
class CrawlActivityChecker {
    private final CrawlContext ctx;
    @Getter
    private final boolean deleting;

    private static boolean isResolving;
    private static boolean isPresumedActive = true;
    private boolean maxDocsReached;

    synchronized boolean isActive() {
        if (!isPresumedActive) {
            return false;
        }
        if (isResolving) {
            return true;
        }
        isResolving = true;
        try {
            isPresumedActive = doIsActive();
        } finally {
            isResolving = false;
        }
        return isPresumedActive;
    }

    private boolean doIsActive() {
        if (isMaxDocsApplicableAndReached()) {
            return false;
        }
        if (isQueueInitializedAndEmpty()) {
            return !isQueueStillEmptyAfterIdleTimeout();
        }
        return true;
    }

    boolean isMaxDocsApplicableAndReached() {
        // If deleting we don't care about checking if max is reached,
        // we proceed.
        if (deleting) {
            return false;
        }

        if (maxDocsReached) {
            return true;
        }

        if (ctx.getDocLedger().isMaxDocsProcessedReached()) {
            LOG.info("Pausing crawler. Will resume on next start.");
            maxDocsReached = true;
            // We update the crawl state to PAUSED so that the crawler
            // can be resumed later if needed.
            // This is done here so that the crawl state is updated
            // before the task is stopped.
            ctx.getGrid().getCompute().executeTask(GridTaskBuilder
                    .create("updateCrawlState")
                    .singleNode()
                    .processor(g -> CrawlContext
                            .get(g)
                            .getSessionProperties()
                            .updateCrawlState(CrawlState.PAUSED))
                    .build());
            return true;
        }
        return false;
    }

    private boolean isQueueInitializedAndEmpty() {
        var sessionStore = ctx.getSessionProperties();
        var queueEmpty = isQueueEmpty();
        if (queueEmpty) {
            var queueInitialized = sessionStore.isQueueInitialized();
            if (!queueInitialized) {
                LOG.info("""
                    References are still being queued. \
                    Waiting for new references or initial queuing \
                    to be over...""");
                do {
                    Sleeper.sleepSeconds(1);
                    queueEmpty = isQueueEmpty();
                    queueInitialized = sessionStore.isQueueInitialized();
                } while (queueEmpty && !queueInitialized);
            }
        }
        if (queueEmpty) {
            LOG.info("Reference queue is empty.");
        }
        return queueEmpty;
    }

    private boolean isQueueStillEmptyAfterIdleTimeout() {
        var duration = ctx.getCrawlConfig().getIdleTimeout();
        if (duration == null || duration.isZero()) {
            return true;
        }

        LOG.info("Waiting up to {} for references to be added to the queue.",
                idleTimeoutAsText());
        var timeout = duration.toMillis();
        var then = System.currentTimeMillis();
        while (System.currentTimeMillis() - then < timeout) {
            Sleeper.sleepSeconds(1);
            if (!isQueueEmpty()) {
                return false;
            }
        }
        LOG.info("This crawler node has been idle for more than {}.",
                idleTimeoutAsText());
        return true;
    }

    private boolean isQueueEmpty() {
        return ctx.getDocLedger().isQueueEmpty();
    }

    private String idleTimeoutAsText() {
        return DurationFormatter.FULL.format(
                ctx.getCrawlConfig().getIdleTimeout());
    }

}
