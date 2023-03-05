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
package com.norconex.crawler.web.pipeline.queue;

import static com.norconex.crawler.web.util.Web.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;

import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.crawler.CrawlerImpl.QueueInitContext;
import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.crawler.web.crawler.StartURLsProvider;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.sitemap.SitemapResolutionContext;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class WebQueueInitializer
        implements Function<QueueInitContext, MutableBoolean> {

    @Override
    public MutableBoolean apply(QueueInitContext ctx) {
        // if we resume, we don't add anything.
        // TODO move this resuming logic to crawler-core and remove
        // isResuming from context?
        // TODO consider storing if init was done when we are resuming and
        // uncomment the following.  Else better not to have it and re-process
        // the queue so we do not skip an incomplete queue initialization
//        if (ctx.isResuming()) {
//            return new MutableBoolean(true);
//        }

        var cfg = config(ctx.getCrawler());

        // TODO move async logic to crawler-core config and crawler? YES
        if (cfg.isStartURLsAsync()) {
            var doneStatus = new MutableBoolean(false);

            var executor = Executors.newSingleThreadExecutor();
            try {
                executor.submit(() -> {
                    MdcUtil.setCrawlerId(ctx.getCrawler().getId());
                    Thread.currentThread().setName(ctx.getCrawler().getId());
                    LOG.info("Queuing start URLs asynchronously.");
                    queueStartURLs(ctx);
                    doneStatus.setTrue();
                    return doneStatus;
                });
                return doneStatus;
            } catch (Exception e) {
                doneStatus.setTrue();
                return doneStatus;
            } finally {
                try {
                    executor.shutdown();
                    executor.awaitTermination(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    LOG.error("Reading of start URLs interrupted.", e);
                    Thread.currentThread().interrupt();
                }
            }
        }
        LOG.info("Queuing start URLs synchronously.");
        queueStartURLs(ctx);
        return new MutableBoolean(true);
    }

    private void queueStartURLs(QueueInitContext ctx) {
        var urlCount = 0;
        // Sitemaps is first as we favor explicit sitemap referencing.
        urlCount += queueStartURLsSitemaps(ctx);
        urlCount += queueStartURLsRegular(ctx);
        urlCount += queueStartURLsSeedFiles(ctx);
        urlCount += queueStartURLsProviders(ctx);
        if (LOG.isInfoEnabled()) {
            LOG.info("{} start URLs identified.",
                    NumberFormat.getNumberInstance().format(urlCount));
        }
    }

    private int queueStartURLsSitemaps(QueueInitContext ctx) {
        var cfg = config(ctx.getCrawler());
        var sitemapURLs = cfg.getStartSitemapURLs();
        var sitemapResolver = cfg.getSitemapResolver();

        // There are sitemaps, process them. First group them by URL root
        MultiValuedMap<String, String> sitemapsPerRoots =
                new ArrayListValuedHashMap<>();
        for (String sitemapURL : sitemapURLs) {
            var urlRoot = HttpURL.getRoot(sitemapURL);
            sitemapsPerRoots.put(urlRoot, sitemapURL);
        }

        final var urlCount = new MutableInt();
        Consumer<WebDocRecord> urlConsumer = rec -> {
            ctx.queue(rec);
            urlCount.increment();
        };
        // Process each URL root group separately
        for (String urlRoot : sitemapsPerRoots.keySet()) {
            var locations = (List<String>) sitemapsPerRoots.get(urlRoot);
            if (sitemapResolver != null) {
                sitemapResolver.resolveSitemaps(SitemapResolutionContext
                        .builder()
                        .fetcher((HttpFetcher) ctx.getCrawler().getFetcher())
                        .sitemapLocations(locations)
                        .startURLs(true)
                        .urlRoot(urlRoot)
                        .urlConsumer(urlConsumer)
                        .build());
            } else {
                LOG.error("Sitemap resolver is null. Sitemaps defined as "
                        + "start URLs cannot be resolved.");
            }
        }
        if (urlCount.intValue() > 0) {
            LOG.info("Queued {} start URLs from {} sitemap(s).",
                    urlCount, sitemapURLs.size());
        }
        return urlCount.intValue();
    }

    private int queueStartURLsRegular(QueueInitContext ctx) {
        var cfg = config(ctx.getCrawler());
        var startURLs = cfg.getStartURLs();
        for (String startURL : startURLs) {
            if (StringUtils.isNotBlank(startURL)) {
                ctx.queue(new WebDocRecord(startURL, 0));
            } else {
                LOG.debug("Blank start URL encountered, ignoring it.");
            }
        }
        if (!startURLs.isEmpty()) {
            LOG.info("Queued {} regular start URLs.", startURLs.size());
        }
        return startURLs.size();
    }

    private int queueStartURLsSeedFiles(QueueInitContext ctx) {
        var urlsFiles = config(ctx.getCrawler()).getStartURLsFiles();
        var urlCount = 0;
        for (Path urlsFile : urlsFiles) {
            try (var it = IOUtils.lineIterator(
                    Files.newInputStream(urlsFile), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var startURL = StringUtils.trimToNull(it.nextLine());
                    if (startURL != null && !startURL.startsWith("#")) {
                        ctx.queue(new WebDocRecord(startURL, 0));
                        urlCount++;
                    }
                }
            } catch (IOException e) {
                throw new CrawlerException(
                        "Could not process URLs file: " + urlsFile, e);
            }
        }
        if (urlCount > 0) {
            LOG.info("Queued {} URLs from {} seed files.",
                    urlCount, urlsFiles.size());
        }
        return urlCount;
    }

    private int queueStartURLsProviders(QueueInitContext ctx) {
        var providers = config(ctx.getCrawler()).getStartURLsProviders();
        if (providers == null) {
            return 0;
        }
        var count = 0;
        for (StartURLsProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            var it = provider.provideStartURLs();
            while (it.hasNext()) {
                ctx.queue(new WebDocRecord(it.next(), 0));
                count++;
            }
        }
        if (count > 0) {
            LOG.info("Queued {} URLs from {} URL providers.",
                    count, providers.size());
        }
        return count;
    }
}
