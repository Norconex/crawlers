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

import java.util.concurrent.atomic.AtomicBoolean;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlState;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Uses a few different criteria to find out if the crawler should still be
 * considered active. When applicable, will wait until that information
 * can be established.
 */
@Slf4j
//TODO still needed? Done by cluster?
class CrawlActivityChecker {
    private final CrawlSession session;
    @Getter
    private final boolean deleting;

    private final long expireAt;

    private static boolean isResolving;
    private static boolean isPresumedActive = true;
    private static AtomicBoolean canContinue = new AtomicBoolean(true);
    //    private boolean maxDocsReached;

    public CrawlActivityChecker(CrawlSession session, boolean deleting) {
        this.session = session;
        this.deleting = deleting;
        var maxDuration = session
                .getCrawlContext().getCrawlConfig().getMaxCrawlDuration();
        expireAt = (maxDuration == null || maxDuration.toMillis() <= 0)
                ? 0
                : System.currentTimeMillis() + maxDuration.toMillis();
    }

    //TODO can we do without synchronize?
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
        if (!canContinue()) {
            return false;
        }
        if (isQueueInitializedAndEmpty()) {
            return !isQueueStillEmptyAfterIdleTimeout();
        }
        return true;
    }

    boolean canContinue() {
        if (!canContinue.get()) {
            return false;
        }

        var can = doCanContinue();
        if (!can) {
            LOG.info("Stopping crawl execution (i.e., pause). Will resume "
                    + "on next start.");
            session.updateCrawlState(CrawlState.STOPPED);
        }

        canContinue.set(can);
        return can;
    }

    boolean doCanContinue() {
        // If deleting we don't care about checking if max is reached,
        // we proceed.
        if (deleting) {
            return true;
        }

        if (isCrawlExpired()) {
            LOG.info("Max crawl duration reached.");
            return false;
        }

        if (session.getCrawlContext().getCrawlEntryLedger()
                .isMaxDocsProcessedReached()) {
            LOG.info("Max number of documents reached.");
            return false;
        }
        return true;
    }

    private boolean isCrawlExpired() {
        return expireAt > 0 && System.currentTimeMillis() > expireAt;
    }

    private boolean isQueueInitializedAndEmpty() {
        var queueEmpty = isQueueEmpty();
        if (queueEmpty) {
            var queueInitialized = session.isStartRefsQueueingComplete();
            if (!queueInitialized) {
                LOG.info("""
                    References are still being queued. \
                    Waiting for new references or initial queuing \
                    to be over...""");
                do {
                    System.err.println("XXX sleep B");
                    Sleeper.sleepSeconds(1);
                    queueEmpty = isQueueEmpty();
                    queueInitialized = session.isStartRefsQueueingComplete();
                } while (queueEmpty && !queueInitialized);
            }
        }
        if (queueEmpty) {
            LOG.info("Reference queue is empty.");
        }
        return queueEmpty;
    }

    private boolean isQueueStillEmptyAfterIdleTimeout() {
        var duration =
                session.getCrawlContext().getCrawlConfig().getIdleTimeout();
        if (duration == null || duration.isZero()) {
            return true;
        }

        LOG.info("Waiting up to {} for references to be added to the queue.",
                idleTimeoutAsText());
        var timeout = duration.toMillis();
        var then = System.currentTimeMillis();
        while (System.currentTimeMillis() - then < timeout) {
            System.err.println("XXX sleep A");
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
        return session.getCrawlContext().getCrawlEntryLedger().isQueueEmpty();
    }

    private String idleTimeoutAsText() {
        return DurationFormatter.FULL.format(
                session.getCrawlContext().getCrawlConfig().getIdleTimeout());
    }

}
