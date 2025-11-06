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
package com.norconex.crawler.core._DELETE.crawler;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.BiConsumer;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.CrawlCallbacks;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cluster.impl.infinispan.CacheNames;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.storeexport.StoreExportCommand;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps user provided crawl driver factory to add auto-exporting of
 * stores when cluster is done.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@Deprecated
public final class CachesExporterCrawlDriverWrapper {

    public static final String EXPORT_REL_DIR = "caches";

    public static CrawlDriver wrap(CrawlDriver delegate) {
        return TestCrawlDriverFactory.builder()
                .beanMapper(delegate.beanMapper())
                .bootstrappers(delegate.bootstrappers())
                .callbacks(wrapCallbacks(delegate.callbacks()))
                .crawlEntryType(delegate.crawlEntryType())
                .crawlerConfigClass(delegate.crawlerConfigClass())
                .crawlPipelineFactory(delegate.crawlPipelineFactory())
                .docPipelines(delegate.docPipelines())
                .eventManager(delegate.eventManager())
                .fetchDriver(delegate.fetchDriver())
                .build();
    }

    private static CrawlCallbacks wrapCallbacks(CrawlCallbacks cbs) {
        var base = cbs == null ? CrawlCallbacks.builder().build() : cbs;
        return CrawlCallbacks.builder()
                .afterCommand(wrapAfterCommand(base.getAfterCommand()))
                .afterDocumentFinalizing(base.getAfterDocumentFinalizing())
                .afterDocumentProcessing(base.getAfterDocumentProcessing())
                // .beforeCommand(wrapBeforeCommand(base.getBeforeCommand()))
                .beforeCommand(base.getBeforeCommand())
                .beforeDocumentFinalizing(base.getBeforeDocumentFinalizing())
                .beforeDocumentProcessing(base.getBeforeDocumentProcessing())
                .build();
    }

    // Export caches
    private static BiConsumer<CrawlSession, Class<? extends Command>>
            wrapAfterCommand(
                    BiConsumer<CrawlSession, Class<? extends Command>> ac) {
        return (session, cmdCls) -> {
            // call original callback if present
            if (ac != null) {
                ac.accept(session, cmdCls);
            }

            if (!session.getCluster().getLocalNode().isCoordinator()) {
                return;
            }

            // Wait for cache synchronization to complete before exporting.
            // Infinispan cache writes are asynchronous and may not be
            // immediately visible for export. We actively poll for the
            // pipeline result to be present in the cache.
            waitForPipelineResultInCache(session);

            LOG.info("Coordinator is exporting cache...");

            // Determine export directory under the configured work dir
            var workDir = session
                    .getCrawlContext()
                    .getCrawlConfig()
                    .getWorkDir();
            var cachesDir = workDir.resolve(EXPORT_REL_DIR);

            try {
                // Ensure directories exist (some commands may not create them)
                Files.createDirectories(cachesDir);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Failed creating caches export directory: "
                                + cachesDir,
                        e);
            }

            // Export all crawler stores for this session
            LOG.info("Exporting crawler caches to {}:{}",
                    session.getCluster().getLocalNode().getNodeName(),
                    cachesDir);
            new StoreExportCommand(cachesDir, false).execute(session);
        };
    }

    /**
     * Actively waits for the pipeline result to appear in the cache.
     * This ensures cache writes have completed before we try to export.
     * Polls up to 2000ms with 50ms intervals.
     */
    private static void waitForPipelineResultInCache(CrawlSession session) {
        var cache = session.getCluster()
                .getCacheManager()
                .getCache(CacheNames.PIPE_CURRENT_STEP,
                        com.norconex.crawler.core.cluster.pipeline.StepRecord.class);

        var maxWaitMs = 2000;
        var pollIntervalMs = 50;
        var startTime = System.currentTimeMillis();

        LOG.info("Waiting for pipeline result in cache (isEmpty={})",
                cache.isEmpty());

        while (System.currentTimeMillis() - startTime < maxWaitMs) {
            // Check if there's any entry in the cache
            if (!cache.isEmpty()) {
                LOG.info("Pipeline result found in cache after {}ms (size={})",
                        System.currentTimeMillis() - startTime,
                        cache.size());
                return; // Cache is synchronized
            }
            Sleeper.sleepMillis(pollIntervalMs);
        }

        // If we timeout, log a warning
        LOG.warn("No pipeline result found in cache after {}ms timeout! "
                + "Cache isEmpty={}, size={}. Proceeding with export anyway.",
                maxWaitMs, cache.isEmpty(), cache.size());
    }

}
