/* Copyright 2020 Norconex Inc.
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

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.canon.ICanonicalLinkDetector;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.url.IURLNormalizer;

/**
 * Unless we are ignoring canonical URL support, checks that a document is
 * canonical or reject it.
 *
 * @author Pascal Essiembre
 * @since 3.0.0 (Merge of former separate canonical stages).
 */
class CanonicalStage extends AbstractHttpMethodStage {
    private static final Logger LOG =
            LoggerFactory.getLogger(CanonicalStage.class);

    public CanonicalStage(HttpMethod method) {
        super(method);
    }

    @Override
    public boolean executeStage(
            HttpImporterPipelineContext ctx, HttpMethod method) {

        ICanonicalLinkDetector detector =
                ctx.getConfig().getCanonicalLinkDetector();

        //Return right away if canonical links are ignored or no detector.
        if (ctx.getConfig().isIgnoreCanonicalLinks() || detector == null) {
            return true;
        }

        // Resolve against headers if not done already
        if (!wasHttpHeadPerformed(ctx) && !resolveFromHeaders(ctx, detector)) {
            return false;
        }

        // Resolve against content if in a GET method
        return method != HttpMethod.GET || resolveFromContent(ctx, detector);
    }

    // Resolves metadata (HTTP headers) canonical link detection
    private boolean resolveFromHeaders(
            HttpImporterPipelineContext ctx, ICanonicalLinkDetector detector) {
        return resolveCanonical(ctx, detector.detectFromMetadata(
                ctx.getDocument().getReference(), ctx.getMetadata()));
    }

    // Proceed with document (<meta>) canonical link detection
    private boolean resolveFromContent(
            HttpImporterPipelineContext ctx, ICanonicalLinkDetector detector) {
        try {
            return resolveCanonical(ctx, detector.detectFromContent(
                    ctx.getDocument().getReference(),
                    ctx.getDocument().getInputStream(),
                    ctx.getDocument().getDocInfo().getContentType()));
        } catch (IOException e) {
            throw new CollectorException(
                    "Cannot resolve canonical link from content for: "
                    + ctx.getDocument().getReference(), e);
        }
    }

    // return true if we process this doc, false if we don't because we
    // will use a canonical URL instead
    private boolean resolveCanonical(
            HttpImporterPipelineContext ctx, String canURL) {

        if (StringUtils.isBlank(canURL)) {
            return true;
        }

        ICanonicalLinkDetector detector =
                ctx.getConfig().getCanonicalLinkDetector();
        HttpDocInfo crawlRef = ctx.getDocInfo();
        String reference = crawlRef.getReference();

        // Since the current/containing page URL has already been
        // normalized, make sure we normalize this one for the purpose
        // of comparing it.  It will them be sent un-normalized to
        // the queue pipeline, since that pipeline performs the
        // normalization after a few other steps.
        String normalizedCanURL = canURL;
        IURLNormalizer normalizer = ctx.getConfig().getUrlNormalizer();
        if (normalizer != null) {
            normalizedCanURL = normalizer.normalizeURL(normalizedCanURL);
        }
        if (normalizedCanURL == null) {
            LOG.info("Canonical URL detected is null after "
                  + "normalization so it will be ignored and its referrer "
                  + "will be processed instead.  Canonical URL: \"{}\" "
                  + "Rererrer URL: {}", canURL, reference);
            return false;
        }

        if (normalizedCanURL.equals(reference)) {
            LOG.debug("Canonical URL detected is the same as document "
                  + "URL. Process normally. URL: {}", reference);
            return true;
        }

        // if circling back here again, we are in a loop, process
        // it regardless
        if (crawlRef.getRedirectTrail().contains(normalizedCanURL)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn(
                    "Circular reference between redirect and canonical "
                  + "URL detected. Will ignore canonical directive and "
                  + "process URL: \"{}\". Redirect trail: {}", reference,
                    Arrays.toString(crawlRef.getRedirectTrail().toArray()));
            }
            return true;
        }

        HttpDocInfo newData = new HttpDocInfo(crawlRef);
        newData.setReference(canURL);
        newData.setReferrerReference(reference);

        if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                crawlRef.getReference(), canURL)) {
            // Call Queue pipeline on Canonical URL
            LOG.debug("Canonical URL detected is different than "
                + "document URL. Document will be rejected while "
                + "canonical URL will be queued for processing: {}",
                canURL);

            HttpQueuePipelineContext newContext =
                    new HttpQueuePipelineContext(ctx.getCrawler(), newData);
            new HttpQueuePipeline().execute(newContext);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Canonical URL not in scope: {}", canURL);
            }
            newData.setState(CrawlState.REJECTED);
            ctx.fireCrawlerEvent(
                    CrawlerEvent.REJECTED_FILTER, newData,
                    ctx.getConfig().getURLCrawlScopeStrategy());
        }

        crawlRef.setState(CrawlState.REJECTED);
        ctx.fireCrawlerEvent(
                HttpCrawlerEvent.REJECTED_NONCANONICAL,
                crawlRef, detector);
        return false;
    }
}