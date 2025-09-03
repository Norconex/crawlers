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

    private static final String CRAWL_RUN_ID_KEY = "crawlRunId";
    private static final String CRAWL_RUN_INFO_KEY = "crawlRunInfo";

    private CrawlRunInfoResolver() {
    }

    /**
     * Resolve the current crawl run info
     * @param session crawl session for which we get the run info
     * @return crawl run info
     */
    public static CrawlRunInfo resolve(CrawlSession session) {
        var runId = resolveCrawlRunId(session);

        return session.oncePerRunAndGet("crawlrun-info", () -> {
            var info = getOrCreateCrawlRunInfo(session, runId);
            if (info.getCrawlResumeState() == CrawlResumeState.NEW) {
                LOG.info("Clearing any previous session-specific "
                        + "cache information");
                session.getCluster()
                        .getCacheManager()
                        .getCrawlSessionCache()
                        .clear();
            }
            save(session, info);
            return info;
        });
    }

    private static String resolveCrawlRunId(CrawlSession session) {
        return session.getCluster()
                .getCacheManager()
                .getCrawlRunCache()
                .computeIfAbsent(
                        CRAWL_RUN_ID_KEY, k -> "cr-" + new ULID().nextULID());
    }

    // Given crawlRunCache is ephemeral, we'll get or create an atomic
    // computeOrGet id. Then the coordinator will compare that one with the
    // one in crawl-session cache and if different will know if we are
    // resuming or not.
    private static CrawlRunInfo getOrCreateCrawlRunInfo(
            CrawlSession session, String runId) {
        var info = load(session).orElse(null);

        if (info == null) {
            LOG.info("No previous crawl session detected. Going fresh.");
            return CrawlRunInfo.builder()
                    .crawlRunId(runId)
                    .crawlSessionId("cs-" + new ULID().nextULID())
                    .crawlerId(session.getCrawlerId())
                    .crawlResumeState(CrawlResumeState.NEW)
                    .crawlMode(CrawlMode.FULL)
                    .build();
        }

        if (Objects.equals(info.getCrawlRunId(), runId)) {
            LOG.info("Found an active crawl session. Joining it.");
            return info;
        }

        var timedState = session.loadState();

        var b = CrawlRunInfo.builder()
                .crawlerId(session.getCrawlerId())
                .crawlRunId(runId);

        return switch (timedState.getCrawlState()) {
            case RUNNING -> {
                LOG.warn("A previously aborted session was detected for "
                        + "crawler {}. Trying to resume it.",
                        session.getCrawlerId());
                yield b.crawlMode(info.getCrawlMode())
                        .crawlSessionId(info.getCrawlSessionId())
                        .crawlResumeState(CrawlResumeState.RESUMED)
                        .build();
            }
            case STOPPED -> {
                LOG.info("A previously stopped crawl session was detected for "
                        + "crawler {}. Resuming it.", session.getCrawlerId());
                yield b.crawlMode(info.getCrawlMode())
                        .crawlSessionId(info.getCrawlSessionId())
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
                yield b.crawlMode(info.getCrawlMode())
                        .crawlSessionId(info.getCrawlSessionId())
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
