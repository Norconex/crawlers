/* Copyright 2010-2016 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.url.ILinkExtractor;
import com.norconex.collector.http.url.Link;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;

/**
 * Extract URLs before sending to importer (because the importer may
 * strip some "valid" urls in producing content-centric material).
 * Plus, any additional urls could be added to Metadata and they will
 * be considered.
 */
/*default*/ class LinkExtractorStage extends AbstractImporterStage {

    private static final Logger LOG =
            LogManager.getLogger(LinkExtractorStage.class);

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {

        Set<Link> links = extractLinks(ctx);
        if (links.isEmpty()) {
            return true;
        }


        String reference = ctx.getCrawlData().getReference();

        Set<String> uniqueExtractedURLs = new HashSet<>();
        Set<String> uniqueQueuedURLs = new HashSet<>();
        Set<String> uniqueOutOfScopeURLs = new HashSet<>();
        if (links != null) {
            for (Link link : links) {
                try {
                    if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                            reference, link.getUrl())) {
                            String queuedURL = queueURL(
                                    link, ctx, uniqueExtractedURLs);
                            if (StringUtils.isNotBlank(queuedURL)) {
                                uniqueQueuedURLs.add(queuedURL);
                            }
                    } else  {
                        if (LOG.isTraceEnabled()) {
                            LOG.trace("URL not in crawl scope: "
                                    + link.getUrl() + " (keep: "
                                    + ctx.getConfig().isKeepOutOfScopeLinks()
                                    + ")");
                        }
                        if(ctx.getConfig().isKeepOutOfScopeLinks()) {
                            uniqueOutOfScopeURLs.add(link.getUrl());
                        }
                    }
                } catch (Exception e) {
                    LOG.warn("Could not queue extracted URL \""
                            + link.getUrl() + "\".", e);
                }
            }
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("uniqueQueuedURLs count: "
                    + uniqueQueuedURLs.size() + ".");
        }
        if (!uniqueQueuedURLs.isEmpty()) {

            String[] referencedUrls =
                    uniqueQueuedURLs.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
            ctx.getMetadata().addString(
                    HttpMetadata.COLLECTOR_REFERENCED_URLS, referencedUrls);
            ctx.getCrawlData().setReferencedUrls(referencedUrls);
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug("uniqueOutOfScopeURLs count: "
                    + uniqueOutOfScopeURLs.size() + ".");
        }
        if (!uniqueOutOfScopeURLs.isEmpty()) {
            ctx.getMetadata().addString(
                   HttpMetadata.COLLECTOR_REFERENCED_URLS_OUT_OF_SCOPE,
                   uniqueOutOfScopeURLs.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
        }

        ctx.fireCrawlerEvent(HttpCrawlerEvent.URLS_EXTRACTED,
                ctx.getCrawlData(), uniqueQueuedURLs);
        return true;
    }

    private Set<Link> extractLinks(HttpImporterPipelineContext ctx) {
        String reference = ctx.getCrawlData().getReference();
        ILinkExtractor[] extractors = ctx.getConfig().getLinkExtractors();
        if (ArrayUtils.isEmpty(extractors)) {
            LOG.debug("No configured link extractor.  No links will be "
                    + "detected.");
            return SetUtils.emptySet();
        }

        if (ctx.getRobotsMeta() != null
                && ctx.getRobotsMeta().isNofollow()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No URLs extracted due to Robots nofollow rule "
                        + "for URL: " + reference);
            }
            return SetUtils.emptySet();
        }

        Set<Link> links = new HashSet<>();
        CachedInputStream is = ctx.getContent();
        ContentType ct = ctx.getDocument().getContentType();
        for (ILinkExtractor extractor : extractors) {
            if (extractor.accepts(reference, ct)) {
                try {
                    Set<Link> extracted =
                            extractor.extractLinks(is, reference, ct);
                    if (extracted != null) {
                        links.addAll(extracted);
                    }
                } catch (Exception e) {
                    LOG.error("Could not extract links from: " + reference, e);
                } finally {
                    is.rewind();
                }
            }
        }
        return links;
    }

    // Executes HttpQueuePipeline if URL not already processed in that page
    // Returns a URL that was not already processed
    private String queueURL(Link link,
            HttpImporterPipelineContext ctx, Set<String> uniqueExtractedURLs) {

        //TODO do we want to add all URLs in a page, or just the valid ones?
        // i.e., those properly formatted.  If we do so, can it prevent
        // weird/custom URLs that some link extractors may find valid?
        if (uniqueExtractedURLs.add(link.getUrl())) {
            HttpCrawlData newURL = new HttpCrawlData(
                    link.getUrl(), ctx.getCrawlData().getDepth() + 1);
            newURL.setReferrerReference(link.getReferrer());
            newURL.setReferrerLinkTag(link.getTag());
            newURL.setReferrerLinkText(link.getText());
            newURL.setReferrerLinkTitle(link.getTitle());
            HttpQueuePipelineContext newContext =
                    new HttpQueuePipelineContext(ctx.getCrawler(),
                            ctx.getCrawlDataStore(), newURL);
            new HttpQueuePipeline().execute(newContext);
            String afterQueueURL = newURL.getReference();
            if (LOG.isDebugEnabled()
                    && !link.getUrl().equals(afterQueueURL)) {
                LOG.debug("URL modified from \"" + link.getUrl()
                        + "\" to \"" + afterQueueURL);
            }
            return afterQueueURL;
        }
        return null;
    }
}