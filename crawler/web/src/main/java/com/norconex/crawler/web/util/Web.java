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
package com.norconex.crawler.web.util;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.pipeline.AbstractPipelineContext;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.crawler.WebCrawlerContext;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.pipeline.importer.WebImporterPipelineContext;
import com.norconex.crawler.web.robot.RobotsTxt;

import lombok.NonNull;

public final class Web {

    private Web() {}

    public static WebCrawlerConfig config(CrawlerConfig cfg) {
        return (WebCrawlerConfig) cfg;
    }
    public static WebCrawlerConfig config(AbstractPipelineContext ctx) {
        return (WebCrawlerConfig) ctx.getConfig();
    }
    public static WebCrawlerConfig config(Crawler crawler) {
        return (WebCrawlerConfig) crawler.getCrawlerConfig();
    }

    public static WebCrawlerContext crawlerContext(Crawler crawler) {
        return (WebCrawlerContext) crawler.getCrawlerContext();
    }

    public static WebImporterPipelineContext importerContext(
            AbstractPipelineContext ctx) {
        return (WebImporterPipelineContext) ctx;
    }

    //TODO could probably move this where needed since generically,
    // we would get the fetcher wrapper directly from crawler.
    public static List<HttpFetcher> toHttpFetcher(
            @NonNull Collection<Fetcher<?, ?>> fetchers) {
        return fetchers.stream()
            .map(HttpFetcher.class::cast)
            .toList();
    }

    public static HttpFetcher fetcher(Crawler crawler) {
        return (HttpFetcher) crawler.getFetcher();
    }

    public static WebDocRecord docRecord(@NonNull CrawlDoc crawlDoc) {
        return (WebDocRecord) crawlDoc.getDocRecord();
    }
    public static WebDocRecord cachedDocRecord(@NonNull CrawlDoc crawlDoc) {
        return (WebDocRecord) crawlDoc.getCachedDocRecord();
    }

    public static RobotsTxt robotsTxt(DocRecordPipelineContext ctx) {
        return robotsTxt(ctx.getCrawler(), ctx.getDocRecord().getReference());
    }
    public static RobotsTxt robotsTxt(Crawler crawler, String reference) {
        var cfg = Web.config(crawler);
        return Optional.ofNullable(cfg.getRobotsTxtProvider())
            .map(rb -> rb.getRobotsTxt(
                    (HttpFetcher) crawler.getFetcher(),
                    reference))
            .orElse(null);
    }
}
