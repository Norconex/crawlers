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
package com.norconex.crawler.web;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.io.IOUtils;

import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcherConfig;
import com.norconex.importer.doc.Doc;

import lombok.NonNull;

public final class WebTestUtil {

    private WebTestUtil() {}

    /**
     * Gets the {@link MemoryCommitter} from first committer of the first
     * crawler from a crawl session (assuming the first committer is
     * a {@link MemoryCommitter}).  If that committer does not
     * exists or is not a memory committer, an exception is thrown.
     * @param crawlSession crawl session
     * @return Memory committer
     */
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull CrawlSession crawlSession) {
        return (MemoryCommitter) getFirstCrawlerConfig(
                crawlSession).getCommitters().get(0);
    }
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull Crawler crawler) {
        return (MemoryCommitter)
                crawler.getCrawlerConfig().getCommitters().get(0);
    }
    public static Crawler getFirstCrawler(
            @NonNull CrawlSession crawlSession) {
        if (!crawlSession.getCrawlers().isEmpty()) {
            return crawlSession.getCrawlers().get(0);
        }
        return null;
    }
    public static WebCrawlerConfig getFirstCrawlerConfig(
            @NonNull CrawlSession crawlSession) {
        return getFirstCrawlerConfig(crawlSession.getCrawlSessionConfig());
    }
    public static WebCrawlerConfig getFirstCrawlerConfig(
            @NonNull CrawlSessionConfig crawlSessionConfig) {
        return (WebCrawlerConfig) crawlSessionConfig.getCrawlerConfigs().get(0);
    }
    public static GenericHttpFetcher getFirstHttpFetcher(
            @NonNull CrawlSession crawlSession) {
        return (GenericHttpFetcher) getFirstCrawlerConfig(
                crawlSession.getCrawlSessionConfig()).getHttpFetchers().get(0);
    }
    public static GenericHttpFetcherConfig getFirstHttpFetcherConfig(
            @NonNull CrawlerConfig crawlerConfig) {
        return ((GenericHttpFetcher) ((WebCrawlerConfig) crawlerConfig)
                .getHttpFetchers().get(0)).getConfig();
    }

    public static void ignoreAllIgnorables(CrawlSession crawlSession) {
        crawlSession.getCrawlSessionConfig().getCrawlerConfigs().forEach(c -> {
            var cfg = (WebCrawlerConfig) c;
            cfg.setIgnoreCanonicalLinks(true);
            cfg.setIgnoreRobotsMeta(true);
            cfg.setIgnoreRobotsTxt(true);
            cfg.setIgnoreSitemap(true);
        });
    }

    public static ZonedDateTime daysAgo(int days) {
        return ZonedDateTime.now().minusDays(days).withNano(0);
    }
    public static String daysAgoRFC(int days) {
        return rfcFormat(daysAgo(days));
    }
    public static String rfcFormat(ZonedDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    public static String docText(Doc doc) {
        return toString(doc.getInputStream());
    }
    public static String docText(UpsertRequest doc) {
        return toString(doc.getContent());
    }
    public static String toString(InputStream is) {
        try {
            return IOUtils.toString(is, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
