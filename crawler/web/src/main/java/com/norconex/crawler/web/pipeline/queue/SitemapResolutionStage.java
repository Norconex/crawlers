/* Copyright 2010-2023 Norconex Inc.
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

import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.apache.commons.collections4.queue.SynchronizedQueue;
import org.apache.commons.lang3.mutable.MutableInt;

import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.sitemap.SitemapContext;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class SitemapResolutionStage implements Predicate<DocRecordPipelineContext> {

    private Queue<String> resolvedWebsites =
            SynchronizedQueue.synchronizedQueue(
                    new CircularFifoQueue<>(10_000) {
                        private static final long serialVersionUID = 1L;
                        @Override
                        public boolean add(String element) {
                            if (contains(element)) {
                                return false;
                            }
                            return super.add(element);
                        }
                    });

    @Override
    public boolean test(DocRecordPipelineContext ctx) { //NOSONAR
        var cfg = Web.config(ctx);

        // Both a sitemap resolver and locator must be set to attempt
        // sitemap discovery and processing for a URL web site.
        if (cfg.getSitemapResolver() == null
                || cfg.getSitemapLocator() == null) {
            return true;
        }


        // On top of any caching the Sitemap resolver implementation might
        // chose to do, we cache here whether a sitemap detection
        // what already performed for a site so we don't do it again.
        // Sitemaps provided as start references are not initially cached.

        String docUrl = ctx.getDocRecord().getReference();
        var urlRoot = HttpURL.getRoot(docUrl);

        // if the queue did not change after addition, it means the sitemap
        // was already resolved for a site, so we abort now.
        if (!resolvedWebsites.add(urlRoot)) {
            return true;
        }

        final var urlCount = new MutableInt();
        Consumer<WebDocRecord> urlConsumer = rec -> {
            ctx.getCrawler().queueDocRecord(rec);
            urlCount.increment();
        };

        // Queue changed, proceed
        for (String location :
                cfg.getSitemapLocator().locations(docUrl, ctx.getCrawler())) {

            var sitemapCtx = SitemapContext.builder()
                .fetcher(Web.fetcher(ctx.getCrawler()))
                .location(location)
                .urlConsumer(urlConsumer)
                .build();
            cfg.getSitemapResolver().resolve(sitemapCtx);

            if (urlCount.intValue() > 0) {
                LOG.info("{} references were extracted from sitemap: {}",
                        urlCount.intValue(), location);
                break;
            }
        }
        return true;
    }
}