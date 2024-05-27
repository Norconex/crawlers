/* Copyright 2010-2024 Norconex Inc.
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

import static com.norconex.crawler.web.crawler.WebCrawlerContext.SitemapPresence.NONE;
import static com.norconex.crawler.web.crawler.WebCrawlerContext.SitemapPresence.PRESENT;
import static com.norconex.crawler.web.crawler.WebCrawlerContext.SitemapPresence.RESOLVING;
import static java.util.Optional.ofNullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.commons.lang.url.HttpURL;
import com.norconex.crawler.core.crawler.CrawlerLifeCycleListener;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.web.crawler.WebCrawlerContext.SitemapPresence;
import com.norconex.crawler.web.crawler.WebCrawlerEvent;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.sitemap.SitemapContext;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class SitemapResolutionStage extends CrawlerLifeCycleListener
        implements Predicate<DocRecordPipelineContext> {

    private static final Map<String, Object> lockTable =
            new ConcurrentHashMap<>();

    @Override
    public boolean test(DocRecordPipelineContext ctx) { //NOSONAR

        var cfg = Web.config(ctx);
        var docRec = (WebDocRecord) ctx.getDocRecord();

        // Both a sitemap resolver and locator must be set (which they are
        // by default) to attempt sitemap discovery and processing for a
        // URL's host name. "stayOnSitemap" is also ignored in such case.
        // If the document is already coming form a sitemap, we also
        // return right away.
        if (cfg.getSitemapResolver() == null
                || cfg.getSitemapLocator() == null
                || docRec.isFromSitemap()) {
            return true;
        }

        // On top of any caching the Sitemap resolver implementation might
        // choose to do, we cache here whether a sitemap detection
        // what already performed for a site so we don't do it again.
        // Sitemaps provided as start references are not initially cached.
        var resolvedSites =
                Web.crawlerContext(ctx.getCrawler()).getResolvedWebsites();
        String docUrl = docRec.getReference();
        var urlRoot = HttpURL.getRoot(docUrl);

        // The first time we resolve a sitemap for a root URL, the presence
        // will be unknown. Only if unknown that we proceed with the resolution.
        // While resolving, we store the initial state to be RESOLVING until
        // we are done processing the sitemap. It will them be
        // either PRESENT, or NONE, based on the presence of at least one
        // sitemap URL or not (we treat empty sitemaps as having no sitemaps).
        var presence = ofNullable(resolvedSites.putIfAbsent(
                urlRoot, RESOLVING)).orElse(RESOLVING);

        // Process sitemap
        if (presence == RESOLVING) {
            synchronized (lockTable.computeIfAbsent(
                    urlRoot, k -> new Object())) {
                try {
                    resolveSitemap(urlRoot, ctx);
                } finally {
                    lockTable.remove(urlRoot);
                }
            }
        }

        // Now that we processed the sitemap, refresh its presence status
        presence = resolvedSites.get(urlRoot);

        // If sitemap presence is NONE, it has been resolved and we ignore
        // "stayOnSitemap". We return right away, accepting the URL.
        if (presence == SitemapPresence.NONE) {
            return true;
        }

        // If sitemap is PRESENT and we "stayOnSitemap", we check whether this
        // document comes from a sitemap, else we reject it.
        if (presence == SitemapPresence.PRESENT
                && cfg.getUrlCrawlScopeStrategy()
                    .getConfiguration().isStayOnSitemap()
                && !docRec.isFromSitemap()) {
            Web.fire(ctx.getCrawler(), b -> b
                    .name(WebCrawlerEvent.REJECTED_NOT_FROM_SITEMAP)
                    .crawlDocRecord(docRec)
                    .source(ctx.getCrawler()));
            return false;
        }
        return true;
    }

    private void resolveSitemap(
            String urlRoot, DocRecordPipelineContext ctx) {
        // check again here in case it was resolved by another thread while
        // waiting.
        var resolvedSites =
                Web.crawlerContext(ctx.getCrawler()).getResolvedWebsites();
        if (resolvedSites.get(urlRoot) != RESOLVING) {
            return;
        }

        var docRec = (WebDocRecord) ctx.getDocRecord();

        // Sitemap never processed, so do it
        final var urlCount = new MutableInt();
        Web.fire(ctx.getCrawler(), b -> b
                .name(WebCrawlerEvent.SITEMAP_RESOLVE_BEGIN)
                .crawlDocRecord(docRec)
                .source(ctx.getCrawler()));

        // To make sure the initial doc is not rejected just because
        // it is not yet identified as being part of the sitemap, we
        // look for it and handle it if encountered.
        var isDocFoundInSitemap = new MutableBoolean(false);

        // Prepare URL consumer
        Consumer<WebDocRecord> urlConsumer = rec -> {
            var actualRec = rec;
            if (isDocFoundInSitemap.isFalse() && StringUtils.equalsAny(
                    rec.getReference(),
                    docRec.getReference(),
                    docRec.getOriginalReference())) {
                actualRec = docRec;
            }

            actualRec.setFromSitemap(true);
            isDocFoundInSitemap.setTrue();
            ctx.getCrawler().queueDocRecord(actualRec);
            var cnt = urlCount.getAndIncrement();
            if ((cnt == 0)) {
                resolvedSites.put(urlRoot, PRESENT);
            }
        };

        // Locate & resolve sitemaps
        String docUrl = docRec.getReference();
        var cfg = Web.config(ctx);
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
                // we break since we deal with the first one discovered
                // (we assume there is only one initial sitemap index per site).
                break;
            }
        }

        String eventMsg;
        if (StringUtils.isBlank(foundLocation.getValue())) {
            eventMsg = "No sitemap found or sitemap was empty for %s."
                    .formatted(urlRoot);
            resolvedSites.put(urlRoot, NONE);
        } else {
            // the presence is already set to PRESENT at this point.
            eventMsg = urlCount.toInteger()
                    + " references were extracted from sitemap: "
                    + foundLocation.getValue();
        }

        Web.fire(ctx.getCrawler(), b -> b
                .name(WebCrawlerEvent.SITEMAP_RESOLVE_END)
                .crawlDocRecord(docRec)
                .source(ctx.getCrawler())
                .subject(urlCount.toInteger())
                .message(eventMsg));
    }
}