/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.api.feature.crawl.model;

import static com.norconex.commons.lang.text.TextMatcher.regex;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

import org.apache.commons.beanutils.PropertyUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.event.Event;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.server.api.feature.crawl.impl.DocPostProcessSink;
import com.norconex.crawler.server.api.feature.crawl.impl.EventListenerSink;
import com.norconex.crawler.server.api.feature.crawl.impl.MemDataStoreEngine;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.delay.DelayResolver;
import com.norconex.crawler.web.delay.impl.GenericDelayResolver;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.impl.webdriver.Browser;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcher;
import com.norconex.crawler.web.fetch.impl.webdriver.WebDriverHttpFetcherConfig;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.ImporterHandler;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.tagger.impl.DeleteTagger;
import com.norconex.importer.handler.tagger.impl.KeepOnlyTagger;
import com.norconex.importer.handler.transformer.impl.NoContentTransformer;

import reactor.core.publisher.FluxSink;

/**
 * Mapper of crawl sample requests to a crawl session configuration.
 */
public final class CrawlSampleRequestMapper {

    public static final int HARD_MAX_DOCS = 10;
    public static final int HARD_MIN_DELAY = 1000;
    public static final long DEFAULT_DELAY = 3000;
    public static final long DEFAULT_MAX_CONTENT_SIZE =
            DataUnit.MB.toBytes(10).longValue();

    private static final String KEY_MESSAGE = "message";
    private static final String KEY_EXCEPTION = "exception";

    private CrawlSampleRequestMapper() {}

    /**
     * Maps a crawl sample request to a crawl session configuration.
     * @param req crawl sample request
     * @param sink flux sink
     * @return crawl session configuration
     */
    public static CrawlSessionConfig mapRequest(
            CrawlSampleRequest req, FluxSink<Object> sink) {

        // Crawler
        var crawlerConfig = buildCrawlerConfig(req);
        applyFluxes(crawlerConfig, req, sink);

        // Crawl Session
        var sessionConfig = new CrawlSessionConfig();
        sessionConfig.setId("session-" + TimeIdGenerator.next());
        sessionConfig.setCrawlerConfigs(List.of(crawlerConfig));

        return sessionConfig;
    }


    // new crawler with default config.
    private static WebCrawlerConfig buildCrawlerConfig(CrawlSampleRequest req) {
        var cfg = new WebCrawlerConfig();
        cfg.setStartReferences(req.getStartUrl());
        cfg.setStartReferencesAsync(true);
        cfg.setId("crawler-" + TimeIdGenerator.next());
        cfg.setDataStoreEngine(new MemDataStoreEngine());
        cfg.setNumThreads(1);
        cfg.setDelayResolver(delayResolver(req.getDelay()));
        cfg.setMaxDocuments(Math.min(req.getMaxDocs(), HARD_MAX_DOCS));
        cfg.setMaxDepth(10);
        cfg.setKeepReferencedLinks(new HashSet<>(req.getKeepReferencedLinks()));
        if (req.isIgnoreRobotRules()) {
            cfg.setRobotsMetaProvider(null);
            cfg.setRobotsTxtProvider(null);
        }
        if (req.isIgnoreSitemap()) {
            cfg.setSitemapLocator(null);
            cfg.setSitemapResolver(null);
        }
        if (req.getFetcher() == CrawlSampleRequest.FetcherType.WEBDRIVER) {
            cfg.setFetchers(webDriverFetcher());
        }
        cfg.setReferenceFilters(
                toReferenceFilters(req.getUrlIncludes(), OnMatch.INCLUDE));
        cfg.setReferenceFilters(
                toReferenceFilters(req.getUrlExcludes(), OnMatch.EXCLUDE));

        var ucss = cfg.getUrlCrawlScopeStrategy();
        ucss.setStayOnDomain(true);
        ucss.setStayOnPort(true);
        ucss.setStayOnProtocol(true);

        //TODO  Set User-Agent, mixing browser + Nx contact +  based on
        // customer profile

        configureImporter(cfg.getImporterConfig(), req);
        return cfg;
    }

    private static void applyFluxes(
            WebCrawlerConfig cfg, CrawlSampleRequest req, FluxSink<Object> sink) {
        if (req.getMaxDocs() > 0) {
            cfg.setPostImportProcessors(new DocPostProcessSink(
                    sink, req.getMaxContentSize()));
        }

        req.getEventMatchers().forEach(
            matcher -> cfg.addEventListener(
                new EventListenerSink(
                    sink,
                    event -> regex(matcher.getName()).matches(event.getName()),
                    event -> transformEvent(matcher, event)
                )
            )
        );
    }

    private static CrawlEventDTO transformEvent(
            CrawlEventMatcher matcher, Event event) {

        var dto = new CrawlEventDTO();
        dto.setName(event.getName());
        var props = dto.getProperties();
        if (StringUtils.isNotBlank(event.getMessage())) {
            props.put(KEY_MESSAGE, event.getMessage());
        }
        if (event.getException() != null) {
            props.put(KEY_EXCEPTION,
                    ExceptionUtil.getFormattedMessages(event.getException()));
        }

        matcher.getProperties().forEach(p -> {
            try {
                var value = Objects.toString(
                        PropertyUtils.getProperty(event, p), null);
                if (value != null) {
                    props.put(p, value);
                }
            } catch (IllegalAccessException | InvocationTargetException
                    | NoSuchMethodException e) {
                // Swallow
            }
        });

        return dto;
    }

    private static void configureImporter(
            ImporterConfig cfg, CrawlSampleRequest req) {
        List<ImporterHandler> postHandlers = new ArrayList<>();
        postHandlers.addAll(toKeepOnlyTaggers(req.getFieldIncludes()));
        postHandlers.addAll(toDeleteTaggers(req.getFieldExcludes()));
        if (req.getMaxContentSize() == 0) {
            postHandlers.add(new NoContentTransformer());
        }
        cfg.setPostParseConsumer(HandlerConsumer.fromHandlers(postHandlers));
    }

    private static List<ReferenceFilter> toReferenceFilters(
            List<String> patterns, OnMatch onMatch) {
        return patterns.stream()
                .<ReferenceFilter>map(pattern -> {
                    var filter = new GenericReferenceFilter();
                    filter.setOnMatch(onMatch);
                    filter.setValueMatcher(TextMatcher.regex(pattern));
                    return filter;
                })
                .toList();
    }

    private static List<KeepOnlyTagger> toKeepOnlyTaggers(
            List<String> patterns) {
        return patterns.stream()
                .<KeepOnlyTagger>map(pattern -> {
                    var tagger = new KeepOnlyTagger();
                    tagger.setFieldMatcher(TextMatcher.regex(pattern));
                    return tagger;
                })
                .toList();
    }

    private static List<DeleteTagger> toDeleteTaggers(
            List<String> patterns) {
        return patterns.stream()
                .<DeleteTagger>map(pattern -> {
                    var tagger = new DeleteTagger();
                    tagger.setFieldMatcher(TextMatcher.regex(pattern));
                    return tagger;
                })
                .toList();
    }

    private static HttpFetcher webDriverFetcher() {
        var cfg = new WebDriverHttpFetcherConfig();
        cfg.setBrowser(Browser.FIREFOX);
        //TODO make configurable via Spring config to implement properly
        return new WebDriverHttpFetcher(cfg);

    }

    private static DelayResolver delayResolver(long delay) {
        var dr = new GenericDelayResolver();
        dr.setDefaultDelay(Math.max(delay, HARD_MIN_DELAY));
        return dr;
    }
}
