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

import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.crawler.CrawlerLifeCycleListener;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.web.crawler.WebCrawlerEvent;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.sitemap.SitemapContext;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class SitemapResolutionStage extends CrawlerLifeCycleListener
        implements Predicate<DocRecordPipelineContext> {

    @Override
    public boolean test(DocRecordPipelineContext ctx) { //NOSONAR


        var cfg = Web.config(ctx);
        var docRec = (WebDocRecord) ctx.getDocRecord();
        var resolvedWebsites =
                Web.crawlerContext(ctx.getCrawler()).getResolvedWebsites();

        // Both a sitemap resolver and locator must be set to attempt
        // sitemap discovery and processing for a URL web site.
        // "stayOnSitemap" is also ignored in such case.
        if (cfg.getSitemapResolver() == null
                || cfg.getSitemapLocator() == null) {
            return true;
        }

        // On top of any caching the Sitemap resolver implementation might
        // chose to do, we cache here whether a sitemap detection
        // what already performed for a site so we don't do it again.
        // Sitemaps provided as start references are not initially cached.

        String docUrl = docRec.getReference();
        var urlRoot = HttpURL.getRoot(docUrl);

        // if the queue did not change after addition, the "put" will return
        // null, meaning the sitemap was already resolved for a site and
        // we abort.
        // We start false until we confirm existence by having at least one
        // URL extracted (we treat empty sitemaps as having no sitemaps).
        var sitemapExists =
                resolvedWebsites.putIfAbsent(urlRoot, Boolean.FALSE);

        // To make sure the initial doc is not rejected just because
        // it is encountered before the sitemap gets processed.
        var isDocFoundInSitemap = new MutableBoolean(false);

        if (sitemapExists == null) {
            // Sitemap never processed, so do it

            final var urlCount = new MutableInt();
            Web.fire(ctx.getCrawler(), b -> b
                    .name(WebCrawlerEvent.SITEMAP_RESOLVE_BEGIN)
                    .crawlDocRecord(docRec)
                    .source(ctx.getCrawler()));
            Consumer<WebDocRecord> urlConsumer = rec -> {
                rec.setFromSitemap(true);
                if (isDocFoundInSitemap.isFalse() && StringUtils.equalsAny(
                        rec.getReference(),
                        docRec.getReference(),
                        docRec.getOriginalReference())) {
                    isDocFoundInSitemap.setTrue();
                }
                ctx.getCrawler().queueDocRecord(rec);
                var cnt = urlCount.getAndIncrement();
                if ((cnt == 0)) {
                    resolvedWebsites.put(urlRoot, Boolean.TRUE);
                }
            };

            var foundLocation = new MutableObject<String>();
            for (String location : cfg.getSitemapLocator().locations(
                    docUrl, ctx.getCrawler())) {

                var sitemapCtx = SitemapContext.builder()
                    .fetcher(Web.fetcher(ctx.getCrawler()))
                    .location(location)
                    .urlConsumer(urlConsumer)
                    .build();
                cfg.getSitemapResolver().resolve(sitemapCtx);

                if (urlCount.intValue() > 0) {
                    foundLocation.setValue(location);
                    LOG.info("{} references were extracted from sitemap: {}",
                            urlCount.intValue(), location);
                    break;
                }
            }

            String eventMsg;
            if (StringUtils.isBlank(foundLocation.getValue())) {
                eventMsg = "No sitemap found or sitemap was empty.";
                sitemapExists = false;
            } else {
                eventMsg = urlCount.toInteger()
                        + " references were extracted from sitemap: "
                        + foundLocation.getValue();
                sitemapExists = true;
            }

            Web.fire(ctx.getCrawler(), b -> b
                    .name(WebCrawlerEvent.SITEMAP_RESOLVE_END)
                    .crawlDocRecord(docRec)
                    .source(ctx.getCrawler())
                    .subject(urlCount.toInteger())
                    .message(eventMsg));
        }


        if (cfg.isStayOnSitemap()
                && sitemapExists.booleanValue()
                && !docRec.isFromSitemap()) {
            if (!isDocFoundInSitemap.booleanValue()) {
                Web.fire(ctx.getCrawler(), b -> b
                        .name(WebCrawlerEvent.REJECTED_NOT_FROM_SITEMAP)
                        .crawlDocRecord(docRec)
                        .source(ctx.getCrawler()));
            }
            return false;
        }
        return true;
    }

}