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
        LOG.debug("Prior run info loaded from cache: {}", prior);
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
            LOG.debug("Loaded state for resume detection: {}", timedState);
            if (timedState == null || timedState.getCrawlState() == null) {
                LOG.warn("Prior run found but state is missing/invalid. "
                        + "Starting fresh.");
                info = newInfoForNewSession(session, runId);
            } else {
                info = buildForNextRun(session, runId, prior, timedState);
            }
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
        } else if (info.getCrawlResumeState() == CrawlResumeState.RESUMED) {
            // When resuming, clear pipeline terminal state so coordinator
            // can restart from where it left off instead of immediately exiting
            LOG.info(
                    "Resuming crawl - clearing terminal pipeline state to allow restart");
            clearPipelineTerminalState(session);
        }
        // Persist to session cache for cross-run reference and durability.
        save(session, info);
        return info;
    }

    private static String resolveCrawlRunId(CrawlSession session) {
        // Always generate a new crawl run id per process start. The
        // previous run id is persisted as part of CrawlRunInfo in the
        // session cache and is used for resume detection. Reusing the
        // same run id across process lifecycles can prevent us from
        // correctly detecting prior STOPPED/FAILED runs.
        var newId = "cr-" + new ULID().nextULID();
        var cache = session.getCluster().getCacheManager().getCrawlRunCache();
        cache.put(CRAWL_RUN_ID_KEY, newId);
        return newId;
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
        var cache = session.getCluster()
                .getCacheManager()
                .getCrawlSessionCache();
        return cache.get(CRAWL_RUN_INFO_KEY)
                .map(json -> SerialUtil.fromJson(json, CrawlRunInfo.class));
    }

    private static void save(CrawlSession session, CrawlRunInfo info) {
        session.getCluster()
                .getCacheManager()
                .getCrawlSessionCache()
                .put(CRAWL_RUN_INFO_KEY, SerialUtil.toJsonString(info));
    }

    /**
     * Clears pipeline terminal state when resuming a crawl. This allows
     * the coordinator to restart processing from where it left off instead
     * of treating the previous STOPPED/FAILED state as terminal and exiting
     * immediately.
     */
    private static void clearPipelineTerminalState(CrawlSession session) {
        var sessCache = session.getCluster()
                .getCacheManager()
                .getCrawlSessionCache();

        // Clear the pipeCurrentStep cache which stores terminal step status
        // The coordinator checks this and exits if it finds a terminal status
        // By clearing it, we force the pipeline to restart from scratch
        var keys = sessCache.keys();
        var pipelineKeys = keys.stream()
                .filter(key -> key.startsWith("pipe-step-"))
                .toList();

        pipelineKeys.forEach(sessCache::remove);

        LOG.debug("Cleared {} pipeline step records for resume",
                pipelineKeys.size());
    }
}
