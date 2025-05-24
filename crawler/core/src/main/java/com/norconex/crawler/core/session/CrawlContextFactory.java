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

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocLedger;
import com.norconex.crawler.core.doc.pipelines.DedupService;
import com.norconex.crawler.core.fetch.MultiFetcher;
import com.norconex.crawler.core.metrics.CrawlerMetricsImpl;
import com.norconex.crawler.core.util.ConfigUtil;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.util.ScopedThreadFactoryCreator;
import com.norconex.importer.Importer;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
final class CrawlContextFactory {

    @Data
    @Accessors(fluent = true)
    @Setter(value = AccessLevel.PACKAGE)
    @Getter(value = AccessLevel.PRIVATE)
    static class Builder {
        private Grid grid;
        private CrawlSession session;
        private CrawlDriver driver;
        private CrawlConfig config;

        CrawlContextFactory build() {
            return new CrawlContextFactory(grid, session, driver, config);
        }
    }

    static Builder builder() {
        return new Builder();
    }

    private final Grid grid;
    private final CrawlSession session;
    private final CrawlDriver driver;
    private final CrawlConfig config;

    CrawlContext create() {
        var workDir = ConfigUtil.resolveWorkDir(config);
        var eventManager = ofNullable(driver.eventManager())
                .orElse(new EventManager());
        var tempDir = ConfigUtil.resolveTempDir(config);
        createDir(tempDir); // Will also create "workdir", temp's parent

        var ctx = CrawlContext.builder()
                .beanMapper(driver.beanMapper())
                .bootstrappers(driver.bootstrappers())
                .callbacks(driver.callbacks())
                .committerService(CommitterService
                        .<CrawlDoc>builder()
                        .committers(config.getCommitters())
                        .eventManager(eventManager)
                        .upsertRequestBuilder(doc -> new UpsertRequest(
                                doc.getReference(),
                                doc.getMetadata(),
                                // InputStream closed by caller
                                doc.getInputStream()))
                        .deleteRequestBuilder(doc -> new DeleteRequest(
                                doc.getReference(),
                                doc.getMetadata()))
                        .build())
                .crawlConfig(config)
                .crawlMode(session.getCrawlMode())
                .dedupService(new DedupService())
                .docContextType(driver.docContextType())
                .docPipelines(driver.docPipelines())
                .docLedger(new CrawlDocLedger())
                .eventManager(eventManager)
                .fetcher(MultiFetcher.builder()
                        .fetchers(config.getFetchers())
                        .maxRetries(config.getFetchersMaxRetries())
                        .responseAggregator(
                                driver.fetchDriver().responseAggregator())
                        .unsuccessfulResponseFactory(
                                driver.fetchDriver()
                                        .unsuccesfulResponseFactory())
                        .build())
                .grid(grid)
                .metrics(new CrawlerMetricsImpl())
                .importer(new Importer(
                        config.getImporterConfig(), eventManager))
                .resumeState(session.getResumeState())
                .sessionProperties(
                        new CrawlSessionProperties(grid, config.getId()))
                .streamFactory(new CachedStreamFactory(
                        (int) config.getMaxStreamCachePoolSize(),
                        (int) config.getMaxStreamCacheSize(),
                        tempDir))
                .tempDir(tempDir)
                .threadFactoryCreator(new ScopedThreadFactoryCreator("crawl"))
                .workDir(workDir)
                .build();

        init(ctx);

        return ctx;
    }

    private void init(CrawlContext ctx) {
        ctx.getEventManager().addListenersFromScan(ctx.getCrawlConfig());
        ctx.getCommitterService().init(CommitterContext
                .builder()
                .setEventManager(ctx.getEventManager())
                .setWorkDir(ctx.getWorkDir().resolve("committer"))
                .setStreamFactory(ctx.getStreamFactory())
                .build());
        ctx.getDocLedger().init(ctx);
        ctx.getDedupService().init(ctx);
        ctx.getMetrics().init(ctx);
        ctx.getImporter().init();
        grid.registerContext(CrawlContext.NAME, ctx);
    }

    private void createDir(Path dir) {
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new CrawlerException("Could not create directory: " + dir, e);
        }
    }
}
