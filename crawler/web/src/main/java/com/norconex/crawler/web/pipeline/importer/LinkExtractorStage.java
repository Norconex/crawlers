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
package com.norconex.crawler.web.pipeline.importer;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.pipeline.importer.AbstractImporterStage;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.web.crawler.WebCrawlerConfig.ReferencedLinkType;
import com.norconex.crawler.web.crawler.WebCrawlerEvent;
import com.norconex.crawler.web.doc.WebDocMetadata;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.link.Link;
import com.norconex.crawler.web.link.LinkExtractor;

import lombok.extern.slf4j.Slf4j;

/**
 * Extract URLs before sending to importer (because the importer may
 * strip some "valid" urls in producing normalized content).
 * Plus, any additional urls could be added to Metadata and they will
 * be considered.
 */
@Slf4j
class LinkExtractorStage extends AbstractImporterStage {
    @Override
    protected boolean executeStage(ImporterPipelineContext context) { //NOSONAR
        var ctx = (WebImporterPipelineContext) context;

        var linkTypes =
                ctx.getConfig().getKeepReferencedLinks();

        // If the current page is the deepest allowed, only extract its URL
        // if configured to do so.
        var maxDepth = ctx.getConfig().getMaxDepth();
        if (maxDepth != -1
                && ctx.getDocRecord().getDepth() == maxDepth
                && !linkTypes.contains(ReferencedLinkType.MAXDEPTH)) {
            return true;
        }

        var links = extractLinks(ctx);
        if (links.isEmpty()) {
            return true;
        }

        var docLinks = new UniqueDocLinks();

        for (Link link : links) {
            handleExtractedLink(ctx, docLinks, link);
        }

        LOG.debug("inScope count: {}.", docLinks.inScope.size());
        if (!docLinks.inScope.isEmpty()) {
            var inScopeUrls = docLinks.inScope.toArray(EMPTY_STRING_ARRAY);
            if (linkTypes.contains(ReferencedLinkType.INSCOPE)) {
                ctx.getDocument().getMetadata().add(
                        WebDocMetadata.REFERENCED_URLS, inScopeUrls);
            }
            ctx.getDocRecord().setReferencedUrls(Arrays.asList(inScopeUrls));
        }

        LOG.debug("outScope count: {}.", docLinks.outScope.size());
        if (!docLinks.outScope.isEmpty()) {
            ctx.getDocument().getMetadata().add(
                   WebDocMetadata.REFERENCED_URLS_OUT_OF_SCOPE,
                   docLinks.outScope.toArray(EMPTY_STRING_ARRAY));
        }

        ctx.fire(CrawlerEvent.builder()
                .name(WebCrawlerEvent.URLS_EXTRACTED)
                .source(ctx.getCrawler())
                .subject(ctx.getDocument().getReference())
                .crawlDocRecord(ctx.getDocRecord())
                .message(Integer.toString(docLinks.inScope.size()))
                .build());
        return true;
    }

    private void handleExtractedLink(WebImporterPipelineContext ctx,
            UniqueDocLinks docLinks, Link link) {

        var linkTypes =
                ctx.getConfig().getKeepReferencedLinks();

        try {
            String reference = ctx.getDocRecord().getReference();

            if (ctx.getConfig().getUrlCrawlScopeStrategy().isInScope(
                    reference, link.getUrl())) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("URL in crawl scope: {} (keep: {})",
                            link.getUrl(), linkTypes);
                }
                var queuedURL = queueURL(link, ctx, docLinks.extracted);
                if (StringUtils.isNotBlank(queuedURL)) {
                    docLinks.inScope.add(queuedURL);
                }
            } else  {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("URL not in crawl scope: {} (keep: {})",
                            link.getUrl(), linkTypes);
                }
                if (linkTypes.contains(ReferencedLinkType.OUTSCOPE)) {
                    docLinks.outScope.add(link.getUrl());
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not queue extracted URL \"{}.",
                    link.getUrl(), e);
        }
    }

    private Set<Link> extractLinks(WebImporterPipelineContext ctx) {
        String reference = ctx.getDocRecord().getReference();
        var extractors = ctx.getConfig().getLinkExtractors();
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
        var is = ctx.getContent();
        var doc = ctx.getDocument();
        for (LinkExtractor extractor : extractors) {
            try {
                var extracted = extractor.extractLinks(doc);
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

    // Executes WebQueuePipeline if URL not already processed in that page
    // Returns a URL that was not already processed
    private String queueURL(Link link,
            WebImporterPipelineContext ctx, Set<String> uniqueExtractedURLs) {

        //TODO do we want to add all URLs in a page, or just the valid ones?
        // i.e., those properly formatted.  If we do so, can it prevent
        // weird/custom URLs that some link extractors may find valid?
        if (uniqueExtractedURLs.add(link.getUrl())) {
            var newURL = new WebDocRecord(
                    link.getUrl(), ctx.getDocRecord().getDepth() + 1);
            newURL.setReferrerReference(link.getReferrer());
            if (!link.getMetadata().isEmpty()) {
                newURL.setReferrerLinkMetadata(link.getMetadata().toString());
            }
            ctx.getCrawler().queueDocRecord(newURL);
            String afterQueueURL = newURL.getReference();
            if (LOG.isDebugEnabled() && !link.getUrl().equals(afterQueueURL)) {
                LOG.debug("URL modified from \"{}\" to \"{}\".",
                        link.getUrl(), afterQueueURL);
            }
            return afterQueueURL;
        }
        return null;
    }

    private static class UniqueDocLinks {
        final Set<String> extracted = new HashSet<>();
        final Set<String> inScope = new HashSet<>();
        final Set<String> outScope = new HashSet<>();
    }
}