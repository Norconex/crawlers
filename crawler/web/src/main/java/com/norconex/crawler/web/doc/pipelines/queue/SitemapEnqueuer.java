/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.queue;

import java.util.function.Consumer;

import org.apache.commons.lang3.mutable.MutableInt;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.QueueBootstrapContext;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.ReferenceEnqueuer;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.sitemap.SitemapContext;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SitemapEnqueuer implements ReferenceEnqueuer {

    @Override
    public int enqueue(QueueBootstrapContext queueInitCtx) {

        var cfg = Web.config(queueInitCtx.getCrawlContext());
        var sitemapURLs = cfg.getStartReferencesSitemaps();
        var sitemapResolver = cfg.getSitemapResolver();

        if (!sitemapURLs.isEmpty() && sitemapResolver == null) {
            throw new ConfigurationException("""
                    One or more sitemap URLs were\s\
                    configured as start references but the sitemap resolver\s\
                    was set to null.
                    """);
        }

        final var urlCount = new MutableInt();
        Consumer<WebCrawlDocContext> urlConsumer = rec -> {
            queueInitCtx.queue(rec);
            urlCount.increment();
        };

        // Process each sitemap URL
        for (String url : sitemapURLs) {
            sitemapResolver.resolve(SitemapContext.builder()
                    .fetcher(queueInitCtx.getCrawlContext().getFetcher())
                    .location(url)
                    .urlConsumer(urlConsumer)
                    .build());
        }
        if (urlCount.intValue() > 0) {
            LOG.info(
                    "Queued {} start references from {} sitemap(s).",
                    urlCount, sitemapURLs.size());
        }
        return urlCount.intValue();
    }
}
