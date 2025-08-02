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
package com.norconex.crawler.core2.context;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core2.CrawlConfig;
import com.norconex.crawler.core2.CrawlDriver;
import com.norconex.crawler.core2.CrawlerException;
import com.norconex.crawler.core2.doc.pipelines.DedupService;
import com.norconex.crawler.core2.fetch.MultiFetcher;
import com.norconex.crawler.core2.ledger.CrawlEntryLedger;
import com.norconex.crawler.core2.metrics.CrawlerMetricsImpl;
import com.norconex.crawler.core2.util.ConfigUtil;
import com.norconex.crawler.core2.util.ScopedThreadFactoryCreator;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.Doc;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class CrawlContextFactory {

    @Data
    @Accessors(fluent = true)
    //    @Setter(value = AccessLevel.PACKAGE)
    //    @Getter(value = AccessLevel.PRIVATE)
    public static class Builder {
        //        private Cluster cluster;
        //        private CrawlSessionSnapshot session;
        private CrawlDriver driver;
        private CrawlConfig config;

        public CrawlContextFactory build() {
            return new CrawlContextFactory(
                    /*cluster, session,*/ driver, config);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    //    private final Cluster cluster;
    //    private final CrawlSessionSnapshot session;
    private final CrawlDriver driver;
    private final CrawlConfig config;

    public CrawlContext create() {
        var workDir = ConfigUtil.resolveWorkDir(config);
        var eventManager = ofNullable(driver.eventManager())
                .orElse(new EventManager());
        var tempDir = ConfigUtil.resolveTempDir(config);
        createDir(tempDir); // Will also create "workdir", temp's parent

        return CrawlContext.builder()
                .beanMapper(driver.beanMapper())
                .bootstrappers(driver.bootstrappers())
                .callbacks(driver.callbacks())
                .committerService(CommitterService
                        .<Doc>builder()
                        .committers(config
                                .getCommitters())
                        .eventManager(eventManager)
                        .upsertRequestBuilder(
                                doc -> new UpsertRequest(
                                        doc.getReference(),
                                        doc.getMetadata(),
                                        // InputStream closed by caller
                                        doc.getInputStream()))
                        .deleteRequestBuilder(
                                doc -> new DeleteRequest(
                                        doc.getReference(),
                                        doc.getMetadata()))
                        .build())
                .crawlConfig(config)
                //                .crawlMode(session.getCrawlMode())
                .dedupService(new DedupService())
                .crawlEntryType(driver.crawlEntryType())
                .docPipelines(driver.docPipelines())
                .crawlEntryLedger(new CrawlEntryLedger())
                .eventManager(eventManager)
                .fetcher(MultiFetcher.builder()
                        .fetchers(config.getFetchers())
                        .maxRetries(config
                                .getFetchersMaxRetries())
                        .responseAggregator(
                                driver.fetchDriver()
                                        .responseAggregator())
                        .unsuccessfulResponseFactory(
                                driver.fetchDriver()
                                        .unsuccesfulResponseFactory())
                        .build())
                //                .cluster(cluster)
                .metrics(new CrawlerMetricsImpl())
                .importer(new Importer(
                        config.getImporterConfig(),
                        eventManager))
                //                .launchMode(session.getLaunchMode())
                //                .sessionProperties(
                //                        new CrawlSessionProperties(cluster,
                //                                config.getId()))
                .streamFactory(new CachedStreamFactory(
                        (int) config.getMaxStreamCachePoolSize(),
                        (int) config.getMaxStreamCacheSize(),
                        tempDir))
                .tempDir(tempDir)
                .threadFactoryCreator(
                        new ScopedThreadFactoryCreator("crawl"))
                .workDir(workDir)
                .build();
        //
        //        init(ctx);
        //
        //        return ctx;
    }

    //    public void init(CrawlContext ctx) {
    //        ctx.getEventManager()
    //                .addListenersFromScan(ctx.getCrawlConfig());
    //        ctx.getCommitterService().init(CommitterContext
    //                .builder()
    //                .setEventManager(ctx.getEventManager())
    //                .setWorkDir(ctx.getWorkDir()
    //                        .resolve("committer"))
    //                .setStreamFactory(ctx.getStreamFactory())
    //                .build());
    //        ctx.getCrawlEntryLedger().init(ctx);
    //        ctx.getDedupService().init(ctx);
    //        ctx.getMetrics().init(ctx);
    //        ctx.getImporter().init();
    //        //grid.registerContext(CrawlContext.NAME, ctx);
    //    }

    private void createDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CrawlerException("Could not create directory: " + dir, e);
        }
    }
}
