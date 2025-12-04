/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.session;

import java.util.Objects;
import java.util.Optional;

import com.norconex.crawler.core.util.SerialUtil;

import de.huxhorn.sulky.ulid.ULID;
import lombok.extern.slf4j.Slf4j;

/**
 * Establishes the current crawl run information specific to this run.
 * Given its immutable nature, it should be resolved when a session starts
 * (node joining a cluster).
 */
@Slf4j
public final class CrawlRunInfoResolver {

    public static final String CRAWL_RUN_ID_KEY = "crawlRunId";
    public static final String CRAWL_RUN_INFO_KEY = "crawlRunInfo";

    private CrawlRunInfoResolver() {
    }

    /**
     * Resolve the current crawl run info
     * @param session crawl session for which we get the run info
     * @return crawl run info
     */
    public static CrawlRunInfo resolve(CrawlSession session) {
        var runId = resolveCrawlRunId(session);
        var runCache = session.getCluster().getCacheManager()
                .getCrawlRunCache();
        var sessCache = session.getCluster().getCacheManager()
                .getCrawlSessionCache();

        // Fast-path: if another node already published the run info for
        // this run in the ephemeral run cache, adopt it.
        var existingJson = runCache.get(CRAWL_RUN_INFO_KEY).orElse(null);
        if (existingJson != null) {
            return SerialUtil.fromJson(existingJson, CrawlRunInfo.class);
        }

        // Try to derive next CrawlRunInfo based on prior persisted state
        var prior = load(session).orElse(null);
        System.err.println("XXX PRIOR RUN: " + prior);
        final CrawlRunInfo info;
        if (prior == null) {
            LOG.info("No active or previous crawl session detected, "
                    + "starting a new one.");
            info = newInfoForNewSession(session, runId);
        } else if (Objects.equals(prior.getCrawlRunId(), runId)) {
            LOG.info("Found an active crawl session. Joining it.");
            info = prior;
        } else {
            var timedState = session.loadState();
            info = buildForNextRun(session, runId, prior, timedState);
        }

        // Elect exactly one creator for this run's info using putIfAbsent.
        var json = SerialUtil.toJsonString(info);
        var prev = runCache.putIfAbsent(CRAWL_RUN_INFO_KEY, json);
        if (prev != null) {
            // Another node won the election; adopt the winning info.
            return SerialUtil.fromJson(prev, CrawlRunInfo.class);
        }

        // We are the elected creator for this run. Perform one-time init.
        if (prior == null) {
            LOG.info(
                    "Clearing any previous session-specific cache information");
            sessCache.clear();
        }
        // Persist to session cache for cross-run reference and durability.
        save(session, info);
        return info;
    }

    private static String resolveCrawlRunId(CrawlSession session) {
        var cache = session.getCluster().getCacheManager().getCrawlRunCache();
        var existing = cache.get(CRAWL_RUN_ID_KEY).orElse(null);
        if (existing != null) {
            return existing;
        }
        var newId = "cr-" + new ULID().nextULID();
        var prev = cache.putIfAbsent(CRAWL_RUN_ID_KEY, newId);
        return prev != null ? prev : newId;
    }

    private static CrawlRunInfo newInfoForNewSession(
            CrawlSession session, String runId) {
        LOG.info("No previous crawl session detected. Going fresh.");
        return CrawlRunInfo.builder()
                .crawlRunId(runId)
                .crawlSessionId("cs-" + new ULID().nextULID())
                .crawlerId(session.getCrawlerId())
                .crawlResumeState(CrawlResumeState.NEW)
                .crawlMode(CrawlMode.FULL)
                .build();
    }

    private static CrawlRunInfo buildForNextRun(
            CrawlSession session,
            String runId,
            CrawlRunInfo previous,
            CrawlSession.State timedState) {
        var b = CrawlRunInfo.builder()
                .crawlerId(session.getCrawlerId())
                .crawlRunId(runId);

        return switch (timedState.getCrawlState()) {
            case RUNNING -> {
                LOG.warn("A previously aborted session was detected for "
                        + "crawler {}. Trying to resume it.",
                        session.getCrawlerId());
                yield b.crawlMode(previous.getCrawlMode())
                        .crawlSessionId(previous.getCrawlSessionId())
                        .crawlResumeState(CrawlResumeState.RESUMED)
                        .build();
            }
            case STOPPED -> {
                LOG.info("A previously stopped crawl session was detected for "
                        + "crawler {}. Resuming it.", session.getCrawlerId());
                yield b.crawlMode(previous.getCrawlMode())
                        .crawlSessionId(previous.getCrawlSessionId())
                        .crawlResumeState(CrawlResumeState.RESUMED)
                        .build();
            }
            case COMPLETED -> {
                LOG.info("""
                    A previously completed crawl session was detected \
                    for crawler {}. Starting a new incremental crawl \
                    session.""", session.getCrawlerId());
                yield b.crawlMode(CrawlMode.INCREMENTAL)
                        .crawlSessionId("cs-" + new ULID().nextULID())
                        .crawlResumeState(CrawlResumeState.NEW)
                        .build();
            }
            case FAILED -> {
                LOG.warn("A crawl session for crawler {} was detected but is "
                        + "marked as failed. Trying to resume it.",
                        session.getCrawlerId());
                yield b.crawlMode(previous.getCrawlMode())
                        .crawlSessionId(previous.getCrawlSessionId())
                        .crawlResumeState(CrawlResumeState.RESUMED)
                        .build();
            }
        };
    }

    private static Optional<CrawlRunInfo> load(CrawlSession session) {
        return session.getCluster()
                .getCacheManager()
                .getCrawlSessionCache()
                .get(CRAWL_RUN_INFO_KEY)
                .map(json -> SerialUtil.fromJson(json, CrawlRunInfo.class));
    }

    private static void save(CrawlSession session, CrawlRunInfo info) {
        session.getCluster()
                .getCacheManager()
                .getCrawlSessionCache()
                .put(CRAWL_RUN_INFO_KEY, SerialUtil.toJsonString(info));
    }
}
