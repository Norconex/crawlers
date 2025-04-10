/* Copyright 2024 Norconex Inc.
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
import com.norconex.crawler.core.CrawlerContext;

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
    private final CrawlerContext ctx;
    @Getter
    private final boolean deleting;

    private static boolean isResolving;
    private static boolean isPresumedActive = true;

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

    public boolean isMaxDocsApplicableAndReached() {
        // If deleting we don't care about checking if max is reached,
        // we proceed.
        if (deleting) {
            return false;
        }
        return ctx.getDocProcessingLedger()
                .isMaxDocsProcessedReached();
    }

    private boolean isQueueInitializedAndEmpty() {
        var queueEmpty = isQueueEmpty();
        if (queueEmpty) {
            var queueInitialized = ctx.isQueueInitialized();
            if (!queueInitialized) {
                LOG.info("""
                    References are still being queued. \
                    Waiting for new references or initial queuing \
                    to be over...""");
                do {
                    Sleeper.sleepSeconds(1);
                    queueEmpty = isQueueEmpty();
                    queueInitialized = ctx.isQueueInitialized();
                } while (queueEmpty && !queueInitialized);
            }
        }
        if (queueEmpty) {
            LOG.info("Reference queue is empty.");
        }
        return queueEmpty;
    }

    private boolean isQueueStillEmptyAfterIdleTimeout() {
        var duration = ctx.getConfiguration().getIdleTimeout();
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
        return ctx.getDocProcessingLedger().isQueueEmpty();
    }

    private String idleTimeoutAsText() {
        return DurationFormatter.FULL.format(
                ctx.getConfiguration().getIdleTimeout());
    }

}
