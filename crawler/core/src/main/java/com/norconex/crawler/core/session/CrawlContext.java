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
package com.norconex.crawler.core.session;

import java.nio.file.Path;
import java.util.List;

import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.crawler.core.CrawlCallbacks;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.CrawlBootstrapper;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.doc.CrawlDocLedger;
import com.norconex.crawler.core.doc.pipelines.CrawlDocPipelines;
import com.norconex.crawler.core.doc.pipelines.DedupService;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.metrics.CrawlerMetrics;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.util.ScopedThreadFactoryCreator;
import com.norconex.importer.Importer;
import com.norconex.importer.doc.DocContext;

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
public class CrawlContext {

    public static final String NAME = "CrawlContext";

    private final DedupService dedupService;
    private final CrawlDocLedger docLedger;
    private final CrawlerMetrics metrics;
    private final CrawlConfig crawlConfig;
    private final BeanMapper beanMapper;
    private final EventManager eventManager;
    private final CrawlCallbacks callbacks;
    private final CommitterService<CrawlDoc> committerService;
    private final Importer importer;
    @Singular
    private final List<CrawlBootstrapper> bootstrappers;
    private final CrawlDocPipelines docPipelines;
    private final Fetcher fetcher;
    private final Grid grid;
    private final Path workDir;
    private final Path tempDir;
    private final CachedStreamFactory streamFactory;
    private final Class<? extends CrawlDocContext> docContextType;
    private final LaunchMode resumeState;
    private final CrawlMode crawlMode;
    private final CrawlSessionProperties sessionProperties;
    private final ScopedThreadFactoryCreator threadFactoryCreator;

    //--- Convenience methods --------------------------------------------------

    public static CrawlContext get(Grid grid) {
        return (CrawlContext) grid.getContext(NAME);
    }

    public String getId() {
        return crawlConfig.getId();
    }

    @Override
    public String toString() {
        return getId();
    }

    public boolean isResumedSession() {
        return getResumeState() == LaunchMode.RESUMED;
    }

    public boolean isIncrementalCrawl() {
        return getCrawlMode() == CrawlMode.INCREMENTAL;
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

    public CrawlDocContext createDocContext(@NonNull String reference) {
        var docContext = ClassUtil.newInstance(docContextType);
        docContext.setReference(reference);
        return docContext;
    }

    public CrawlDocContext createDocContext(
            @NonNull DocContext parentContext) {
        var docContext = createDocContext(parentContext.getReference());
        docContext.copyFrom(parentContext);
        return docContext;
    }

}
