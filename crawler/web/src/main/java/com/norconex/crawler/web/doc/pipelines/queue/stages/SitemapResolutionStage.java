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
package com.norconex.crawler.web.doc.pipelines.queue.stages;

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
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.event.listeners.CrawlerLifeCycleListener;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.scope.UrlScopeResolver;
import com.norconex.crawler.web.doc.operations.scope.UrlScopeResolver.SitemapPresence;
import com.norconex.crawler.web.doc.operations.sitemap.SitemapContext;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SitemapResolutionStage extends CrawlerLifeCycleListener
        implements Predicate<QueuePipelineContext> {

    private static final Map<String, Object> lockTable =
            new ConcurrentHashMap<>();

    private GridMap<SitemapPresence> resolvedSites;

    private synchronized void ensureCache(QueuePipelineContext ctx) {
        if (resolvedSites == null) {
            resolvedSites = Web.gridCache(
                    ctx.getCrawlerContext(),
                    UrlScopeResolver.RESOLVED_SITES_CACHE_NAME,
                    SitemapPresence.class);
        }
    }

    @Override
    public boolean test(QueuePipelineContext ctx) { //NOSONAR
        ensureCache(ctx);
        var cfg = Web.config(ctx.getCrawlerContext());
        var docRec = (WebCrawlDocContext) ctx.getDocContext();
        //        var resolvedSites = Web.gridCache(
        //                ctx.getCrawlerContext(),
        //                UrlScopeResolver.RESOLVED_SITES_CACHE_NAME,
        //                SitemapPresence.class);
        //
        //                ctx.getCrawlerContext().getGrid().getr
        //                (WebCrawlerSessionAttributes) ctx.getCrawlerContext()
        //                        .getAttributes();

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
        String docUrl = docRec.getReference();
        var urlRoot = HttpURL.getRoot(docUrl);

        // The first time we resolve a sitemap for a root URL, the presence
        // will be unknown. Only if unknown that we proceed with the resolution.
        // While resolving, we store the initial state to be RESOLVING until
        // we are done processing the sitemap. It will them be
        // either PRESENT, or NONE, based on the presence of at least one
        // sitemap URL or not (we treat empty sitemaps as having no sitemaps).

        var presenceRef = new MutableObject<SitemapPresence>();
        resolvedSites.update(urlRoot, pres -> {
            presenceRef.setValue(
                    ofNullable(pres).orElse(SitemapPresence.RESOLVING));
            return presenceRef.getValue();
        });
        var presence = presenceRef.getValue();

        //        var presence = ofNullable(
        //                resolvedSites
        //                        .getResolvedWebsites()
        //                        .putIfAbsent(
        //                                urlRoot, SitemapPresence.RESOLVING))
        //                                        .orElse(SitemapPresence.RESOLVING);

        // Process sitemap
        if (presence == SitemapPresence.RESOLVING) {
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

        // If sitemap is PRESENT and we "stayOnSitemap", we run a scope
        // check regardless, even if we know most checks are likely to pass.
        if (presence == SitemapPresence.PRESENT && !docRec.isFromSitemap()) {
            var urlScope = cfg.getUrlScopeResolver().resolve(docUrl, docRec);
            Web.fireIfUrlOutOfScope(ctx.getCrawlerContext(), docRec, urlScope);
            return urlScope.isInScope();
        }
        return true;
    }

    private void resolveSitemap(String urlRoot, QueuePipelineContext ctx) {
        // check again here in case it was resolved by another thread while
        // waiting.
        //        var crawlerContext = Web.sessionAttributes(ctx.getCrawlerContext());
        //
        //        if (crawlerContext
        //                .getResolvedWebsites()
        //                .get(urlRoot) != SitemapPresence.RESOLVING) {
        //            return;
        //        }
        //
        if (resolvedSites.get(urlRoot) != SitemapPresence.RESOLVING) {
            return;
        }

        var docRec = (WebCrawlDocContext) ctx.getDocContext();

        // Sitemap never processed, so do it
        final var urlCount = new MutableInt();
        ctx.getCrawlerContext().fire(
                CrawlerEvent
                        .builder()
                        .name(WebCrawlerEvent.SITEMAP_RESOLVE_BEGIN)
                        .docContext(docRec)
                        .source(ctx.getCrawlerContext())
                        .build());

        // To make sure the initial doc is not rejected just because
        // it is not yet identified as being part of the sitemap, we
        // look for it and handle it if encountered.
        var isDocFoundInSitemap = new MutableBoolean(false);

        // Prepare URL consumer
        Consumer<WebCrawlDocContext> urlConsumer = rec -> {
            var actualRec = rec;
            if (isDocFoundInSitemap.isFalse() && StringUtils.equalsAny(
                    rec.getReference(),
                    docRec.getReference(),
                    docRec.getOriginalReference())) {
                actualRec = docRec;
            }

            actualRec.setFromSitemap(true);
            isDocFoundInSitemap.setTrue();
            ctx.getCrawlerContext()
                    .getPipelines()
                    .getQueuePipeline()
                    .accept(
                            new QueuePipelineContext(
                                    ctx.getCrawlerContext(),
                                    actualRec));

            var cnt = urlCount.getAndIncrement();
            if ((cnt == 0)) {
                resolvedSites.put(urlRoot, SitemapPresence.PRESENT);
                //                crawlerContext
                //                        .getResolvedWebsites()
                //                        .put(urlRoot, SitemapPresence.PRESENT);
            }
        };

        // Locate & resolve sitemaps
        String docUrl = docRec.getReference();
        var cfg = Web.config(ctx.getCrawlerContext());
        var foundLocation = new MutableObject<String>();
        for (String location : cfg.getSitemapLocator().locations(
                docUrl, ctx.getCrawlerContext())) {

            var sitemapCtx = SitemapContext.builder()
                    .fetcher(Web.fetcher(ctx.getCrawlerContext()))
                    .location(location)
                    .urlConsumer(urlConsumer)
                    .build();
            cfg.getSitemapResolver().resolve(sitemapCtx);

            if (urlCount.intValue() > 0) {
                foundLocation.setValue(location);
                LOG.info(
                        "{} references were extracted from sitemap: {}",
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
            resolvedSites.put(urlRoot, SitemapPresence.NONE);
            //            crawlerContext
            //                    .getResolvedWebsites()
            //                    .put(urlRoot, SitemapPresence.NONE);
        } else {
            // the presence is already set to PRESENT at this point.
            eventMsg = urlCount.toInteger()
                    + " references were extracted from sitemap: "
                    + foundLocation.getValue();
        }

        ctx.getCrawlerContext().fire(
                CrawlerEvent
                        .builder()
                        .name(WebCrawlerEvent.SITEMAP_RESOLVE_END)
                        .source(ctx.getCrawlerContext())
                        .subject(urlCount.toInteger())
                        .message(eventMsg)
                        .build());
    }
}
