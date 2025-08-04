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
package com.norconex.crawler.core2.context;

import static com.norconex.crawler.core2.util.ExceptionSwallower.swallow;

import java.io.Closeable;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.slf4j.MDC;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core2.CrawlCallbacks;
import com.norconex.crawler.core2.CrawlConfig;
import com.norconex.crawler.core2.cmd.crawl.pipeline.bootstrap.CrawlBootstrapper;
import com.norconex.crawler.core2.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core2.doc.pipelines.DedupService;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.fetch.Fetcher;
import com.norconex.crawler.core2.ledger.CrawlEntry;
import com.norconex.crawler.core2.ledger.CrawlEntryLedger;
import com.norconex.crawler.core2.metrics.CrawlerMetrics;
import com.norconex.crawler.core2.session.CrawlMode;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.session.LaunchMode;
import com.norconex.crawler.core2.util.ConfigUtil;
import com.norconex.crawler.core2.util.ExceptionSwallower;
import com.norconex.crawler.core2.util.ScopedThreadFactoryCreator;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.Doc;

import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Crawler class holding state properties required for running commands and
 * tasks.
 * </p>
 * <h2>JMX Support</h2>
 * <p>
 * JMX support is disabled by default. To enable it, set the system property
 * "enableJMX" to <code>true</code>. You can do so by adding this to your Java
 * launch command:
 * </p>
 *
 * <pre>
 *     -DenableJMX=true
 * </pre>
 */
@EqualsAndHashCode
@Getter
@Slf4j
@Builder(builderClassName = "Builder", toBuilder = true)
@NonNull
public class CrawlContext implements Closeable {

    public static final String NAME = "CrawlContext";

    private final DedupService dedupService;
    private final CrawlEntryLedger crawlEntryLedger;
    private final CrawlerMetrics metrics;
    private final CrawlConfig crawlConfig;
    private final BeanMapper beanMapper;
    private final EventManager eventManager;
    private final CrawlCallbacks callbacks;
    private final CommitterService<Doc> committerService;
    private final Importer importer;
    @Singular
    private final List<CrawlBootstrapper> bootstrappers;
    private final CrawlDocPipelines docPipelines;
    private final Fetcher fetcher;
    //    private final Cluster cluster;
    private final Path workDir;
    private final Path tempDir;
    private final CachedStreamFactory streamFactory;
    private final Class<? extends CrawlEntry> crawlEntryType;
    private final LaunchMode launchMode;
    private final CrawlMode crawlMode;
    //    private final CrawlSessionProperties sessionProperties;
    private final ScopedThreadFactoryCreator threadFactoryCreator;

    //--- Convenience methods --------------------------------------------------

    //    public static CrawlContext get(Grid grid) {
    //        return (CrawlContext) grid.getContext(NAME);
    //    }
    //
    /**
     * Gets the crawler id.
     * @return crawler id
     */
    public String getId() {
        return crawlConfig.getId();
    }

    @Override
    public String toString() {
        return getId();
    }

    public boolean isResumedSession() {
        return launchMode == LaunchMode.RESUMED;
    }

    public boolean isIncrementalCrawl() {
        return crawlMode == CrawlMode.INCREMENTAL;
    }

    //TODO keep "fire" methods on event manager and not here?
    public void fire(Event event) {
        getEventManager().fire(event);
    }

    public void fire(String eventName) {
        fire(CrawlerEvent.builder().name(eventName).source(this).build());
    }

    public void fire(String eventName, Object subject) {
        fire(CrawlerEvent.builder()
                .name(eventName)
                .source(this)
                .subject(subject)
                .build());
    }

    public CrawlEntry createCrawlEntry(@NonNull String reference) {
        var docContext = ClassUtil.newInstance(crawlEntryType);
        docContext.setReference(reference);
        return docContext;
    }

    public CrawlEntry createDocContext(@NonNull CrawlEntry parentEntry) {
        var crawlEntry = createCrawlEntry(parentEntry.getReference());
        BeanUtil.copyProperties(crawlEntry, parentEntry);
        return crawlEntry;
    }

    public void init(CrawlSession session) {
        getEventManager().addListenersFromScan(getCrawlConfig());
        getCommitterService().init(CommitterContext
                .builder()
                .setEventManager(getEventManager())
                .setWorkDir(getWorkDir().resolve("committer"))
                .setStreamFactory(getStreamFactory())
                .build());
        getCrawlEntryLedger().init(session);
        getDedupService().init(session);
        getMetrics().init(session);
        getImporter().init();
        //grid.registerContext(CrawlContext.NAME, ctx);
    }

    // closes associated resources
    @Override
    public void close() {
        LOG.info("Closing CrawlContext...");
        swallow(getImporter()::close);

        // Defer shutdown
        swallow(() -> Optional.ofNullable(
                getCrawlConfig().getDeferredShutdownDuration())
                .filter(d -> d.toMillis() > 0)
                .ifPresent(d -> {
                    LOG.info("Deferred shutdown requested. Pausing for {} "
                            + "starting from this UTC moment: {}",
                            DurationFormatter.FULL.format(d),
                            LocalDateTime.now(ZoneOffset.UTC));
                    Sleeper.sleepMillis(d.toMillis());
                    LOG.info("Shutdown resumed.");
                }));

        ExceptionSwallower.close(getCommitterService());

        swallow(MDC::clear);
        ExceptionSwallower.close(getMetrics()::close);
        var tempDir = ConfigUtil.resolveTempDir(getCrawlConfig());
        swallow(() -> {
            if (tempDir != null) {
                FileUtil.delete(tempDir.toFile());
            }
        }, "Could not delete the temporary directory:" + tempDir);

        swallow(getEventManager()::clearListeners);
        LOG.info("CrawlContext closed.");
    }
}
