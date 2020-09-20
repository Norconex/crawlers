/* Copyright 2010-2020 Norconex Inc.
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

import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.crawler.HttpCrawlerConfig.KeepLinks;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.link.ILinkExtractor;
import com.norconex.collector.http.link.Link;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.parser.ParseState;

/**
 * Extract URLs before sending to importer (because the importer may
 * strip some "valid" urls in producing normalized content).
 * Plus, any additional urls could be added to Metadata and they will
 * be considered.
 */
/*default*/ class LinkExtractorStage extends AbstractImporterStage {

    private static final Logger LOG =
            LoggerFactory.getLogger(LinkExtractorStage.class);

    @Override
    public boolean executeStage(HttpImporterPipelineContext ctx) {
        Set<Link> links = extractLinks(ctx);
        if (links.isEmpty()) {
            return true;
        }

        KeepLinks keepLinks = Optional.ofNullable(ctx.getConfig()
                .getKeepReferencedLinks()).orElse(KeepLinks.INSCOPE);
        UniqueDocLinks docLinks = new UniqueDocLinks();

        for (Link link : links) {
            handleExtractedLink(ctx, docLinks, link, keepLinks);
        }

        LOG.debug("inScope count: {}.", docLinks.inScope.size());
        if (!docLinks.inScope.isEmpty()) {
            String[] inScopeUrls = docLinks.inScope.toArray(EMPTY_STRING_ARRAY);
            if (keepLinks.keepInScope()) {
                ctx.getMetadata().add(
                        HttpDocMetadata.REFERENCED_URLS, inScopeUrls);
            }
            ctx.getDocInfo().setReferencedUrls(Arrays.asList(inScopeUrls));
        }

        LOG.debug("outScope count: {}.", docLinks.outScope.size());
        if (!docLinks.outScope.isEmpty()) {
            ctx.getMetadata().add(
                   HttpDocMetadata.REFERENCED_URLS_OUT_OF_SCOPE,
                   docLinks.outScope.toArray(EMPTY_STRING_ARRAY));
        }

        ctx.fire(HttpCrawlerEvent.URLS_EXTRACTED,b -> b
                .crawlDocInfo(ctx.getDocInfo())
                .message(Integer.toString(docLinks.inScope.size())));
        return true;
    }

    private void handleExtractedLink(HttpImporterPipelineContext ctx,
            UniqueDocLinks docLinks, Link link, KeepLinks keepLinks) {
        try {
            String reference = ctx.getDocInfo().getReference();

            if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                    reference, link.getUrl())) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("URL in crawl scope: {} (keep: {})",
                            link.getUrl(), keepLinks);
                }
                String queuedURL = queueURL(link, ctx, docLinks.extracted);
                if (StringUtils.isNotBlank(queuedURL)) {
                    docLinks.inScope.add(queuedURL);
                }
            } else  {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("URL not in crawl scope: {} (keep: {})",
                            link.getUrl(), keepLinks);
                }
                if (keepLinks.keepOutScope()) {
                    docLinks.outScope.add(link.getUrl());
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not queue extracted URL \"{}.",
                    link.getUrl(), e);
        }
    }

    private Set<Link> extractLinks(HttpImporterPipelineContext ctx) {
        String reference = ctx.getDocInfo().getReference();
        List<ILinkExtractor> extractors = ctx.getConfig().getLinkExtractors();
        if (extractors.isEmpty()) {
            LOG.debug("No configured link extractor.  No links will be "
                    + "extracted.");
            return SetUtils.emptySet();
        }

        if (ctx.getRobotsMeta() != null
                && ctx.getRobotsMeta().isNofollow()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("No URLs extracted due to Robots nofollow rule "
                        + "for URL: {}", reference);
            }
            return SetUtils.emptySet();
        }

        Set<Link> links = new HashSet<>();
        CachedInputStream is = ctx.getContent();
        CrawlDoc doc = ctx.getDocument();
        for (ILinkExtractor extractor : extractors) {
            try {
                Set<Link> extracted =
                        extractor.extractLinks(doc, ParseState.PRE);
                if (extracted != null) {
                    links.addAll(extracted);
                }
            } catch (Exception e) {
                LOG.error("Could not extract links from: " + reference, e);
            } finally {
                is.rewind();
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
            HttpDocInfo newURL = new HttpDocInfo(
                    link.getUrl(), ctx.getDocInfo().getDepth() + 1);
            newURL.setReferrerReference(link.getReferrer());
            if (!link.getMetadata().isEmpty()) {
                newURL.setReferrerLinkMetadata(link.getMetadata().toString());
            }
//            newURL.setReferrerLinkTag(link.getTag());
//            newURL.setReferrerLinkText(link.getText());
//            newURL.setReferrerLinkTitle(link.getTitle());
            HttpQueuePipelineContext newContext =
                    new HttpQueuePipelineContext(ctx.getCrawler(), newURL);
            new HttpQueuePipeline().execute(newContext);
            String afterQueueURL = newURL.getReference();
            if (LOG.isDebugEnabled() && !link.getUrl().equals(afterQueueURL)) {
                LOG.debug("URL modified from \"{}\" to \"{}\".",
                        link.getUrl(), afterQueueURL);
            }
            return afterQueueURL;
        }
        return null;
    }

    private class UniqueDocLinks {
        final Set<String> extracted = new HashSet<>();
        final Set<String> inScope = new HashSet<>();
        final Set<String> outScope = new HashSet<>();
    }
}