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
package com.norconex.crawler.core.junit.cluster.node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.CrawlCallbacks;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core._DELETE.cluster.impl.infinispan.FastLocalInfinispanClusterConnector;
import com.norconex.crawler.core.cluster.impl.infinispan.CacheNames;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.crawl.CrawlCommand;
import com.norconex.crawler.core.cmd.storeexport.StoreExportCommand;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wraps provided crawl driver supplier to add features necessary for testing.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class CrawlDriverInstrumentor {

    private static final String CACHES_DIR = "caches";

    public static CrawlDriver instrument(CrawlDriver delegate) {
        return TestCrawlDriverFactory.builder()
                .beanMapper(delegate.beanMapper())
                .bootstrappers(delegate.bootstrappers())
                .callbacks(callbacks(delegate.callbacks()))
                .crawlEntryType(delegate.crawlEntryType())
                .crawlerConfigClass(delegate.crawlerConfigClass())
                .crawlPipelineFactory(delegate.crawlPipelineFactory())
                .docPipelines(delegate.docPipelines())
                .eventManager(delegate.eventManager())
                .fetchDriver(delegate.fetchDriver())
                .build();
    }

    private static CrawlCallbacks callbacks(CrawlCallbacks cbs) {
        var base = cbs == null ? CrawlCallbacks.builder().build() : cbs;
        return CrawlCallbacks.builder()
                .beforeSession(beforeSession(base.getBeforeSession()))
                .beforeCommand(beforeCommand(base.getBeforeCommand()))
                .beforeDocumentProcessing(base.getBeforeDocumentProcessing())
                .afterDocumentProcessing(base.getAfterDocumentProcessing())
                .beforeDocumentFinalizing(base.getBeforeDocumentFinalizing())
                .afterDocumentFinalizing(base.getAfterDocumentFinalizing())
                .afterCommand(afterCommand(base.getAfterCommand()))
                .afterSession(base.getAfterSession())
                .build();
    }

    // Export events
    private static Consumer<CrawlConfig> beforeSession(
            Consumer<CrawlConfig> bs) {
        return cfg -> {
            var nodeWorkDir = Path.of(
                    System.getProperty(CrawlerNode.PROP_NODE_WORKDIR));
            cfg.setWorkDir(nodeWorkDir);

            // Set FastLocalInfinispanClusterConnector if not already set
            // This avoids serialization issues and ensures all test nodes
            // use the optimized local cluster connector
            if (cfg.getClusterConnector() == null) {
                cfg.setClusterConnector(
                        new FastLocalInfinispanClusterConnector());
            }

            // call original if present
            if (bs != null) {
                bs.accept(cfg);
            }

            if (Boolean.getBoolean(CrawlerNode.PROP_EXPORT_EVENTS)) {
                cfg.addEventListener(new NodeEventsExporter(nodeWorkDir));
            }

            NodeState.init(nodeWorkDir);
        };
    }

    // store number of nodes in cluster
    private static BiConsumer<CrawlSession, Class<? extends Command>>
            beforeCommand(
                    BiConsumer<CrawlSession, Class<? extends Command>> bc) {

        final var executor =
                Executors.newSingleThreadScheduledExecutor(r -> {
                    var t = new Thread(r, "Node count monitor");
                    t.setDaemon(true); // dies with the JVM
                    return t;
                });

        var lastNodeCount = new AtomicInteger();
        return (session, cmdCls) -> {

            executor.scheduleAtFixedRate(() -> {
                var nodeCount = session.getCluster().getNodeCount();
                if (lastNodeCount.intValue() != nodeCount) {
                    LOG.info("Node count changed: {} -> {}",
                            lastNodeCount.intValue(), nodeCount);
                    lastNodeCount.set(nodeCount);
                    NodeState.props().set(NodeState.NODE_COUNT, nodeCount);
                }
            }, 0, 200, TimeUnit.MILLISECONDS);

            //            NodeState.props().set(NodeState.NODE_COUNT_AT_JOIN,
            //                    session.getCluster().getNodeCount());

            // call original callback if present
            if (bc != null) {
                bc.accept(session, cmdCls);
            }
        };

    }

    // Export caches
    private static BiConsumer<CrawlSession, Class<? extends Command>>
            afterCommand(
                    BiConsumer<CrawlSession, Class<? extends Command>> ac) {
        if (!Boolean.getBoolean(CrawlerNode.PROP_EXPORT_CACHES)) {
            return ac;
        }

        return (session, cmdCls) -> {
            // call original callback if present
            if (ac != null) {
                ac.accept(session, cmdCls);
            }

            // We only export caches from the coordinator and if the
            // command is a crawl command
            if (!CrawlCommand.class.equals(cmdCls)
                    || !session.getCluster().getLocalNode().isCoordinator()) {
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
            var cachesDir = workDir.resolve(CACHES_DIR);

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
