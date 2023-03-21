/* Copyright 2020-2023 Norconex Inc.
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

import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.fetch.FetchDirective;
import com.norconex.crawler.core.pipeline.importer.AbstractImporterStage;
import com.norconex.crawler.core.pipeline.importer.ImporterPipelineContext;
import com.norconex.crawler.web.canon.CanonicalLinkDetector;
import com.norconex.crawler.web.crawler.WebCrawlerEvent;
import com.norconex.crawler.web.doc.WebDocRecord;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;

/**
 * Unless we are ignoring canonical URL support, checks that a document is
 * canonical or reject it.
 *
 * @since 3.0.0 (Merge of former separate canonical stages).
 */
@Log4j2
class CanonicalStage extends AbstractImporterStage {

    public CanonicalStage(@NonNull FetchDirective directive) {
        super(directive);
    }

    @Override
    protected boolean executeStage(ImporterPipelineContext context) {
        var ctx = (WebImporterPipelineContext) context;

        var detector = ctx.getConfig().getCanonicalLinkDetector();

        //Return right away if canonical links are ignored or no detector.
        if (ctx.getConfig().isIgnoreCanonicalLinks() || detector == null) {
            return true;
        }

        // Resolve against headers if not done already
        if (!ctx.wasMetadataDirectiveExecuted(getFetchDirective())
                && !resolveFromHeaders(ctx, detector)) {
            return false;
        }

        // Resolve against content if in a GET method
        return getFetchDirective() != FetchDirective.DOCUMENT
                || resolveFromContent(ctx, detector);
    }

    // Resolves metadata (HTTP headers) canonical link detection
    private boolean resolveFromHeaders(
            WebImporterPipelineContext ctx, CanonicalLinkDetector detector) {
        return resolveCanonical(ctx, detector.detectFromMetadata(
                ctx.getDocument().getReference(),
                ctx.getDocument().getMetadata()));
    }

    // Proceed with document (<meta>) canonical link detection
    private boolean resolveFromContent(
            WebImporterPipelineContext ctx, CanonicalLinkDetector detector) {
        try {
            return resolveCanonical(ctx, detector.detectFromContent(
                    ctx.getDocument().getReference(),
                    ctx.getDocument().getInputStream(),
                    ctx.getDocument().getDocRecord().getContentType()));
        } catch (IOException e) {
            throw new CrawlerException(
                    "Cannot resolve canonical link from content for: "
                    + ctx.getDocument().getReference(), e);
        }
    }

    // return true if we process this doc, false if we don't because we
    // will use a canonical URL instead
    private boolean resolveCanonical(
            WebImporterPipelineContext ctx, String canURL) {

        if (StringUtils.isBlank(canURL)) {
            return true;
        }

        var detector =
                ctx.getConfig().getCanonicalLinkDetector();
        var docRec = ctx.getDocRecord();
        String reference = docRec.getReference();

        // Since the current/containing page URL has already been
        // normalized, make sure we normalize this one for the purpose
        // of comparing it.  It will them be sent un-normalized to
        // the queue pipeline, since that pipeline performs the
        // normalization after a few other steps.
        var normalizedCanURL = canURL;
        var normalizer = ctx.getConfig().getUrlNormalizer();
        if (normalizer != null) {
            normalizedCanURL = normalizer.normalizeURL(normalizedCanURL);
        }
        if (normalizedCanURL == null) {
            LOG.info("""
                Canonical URL detected is null after\s\
                normalization so it will be ignored and its referrer\s\
                will be processed instead.  Canonical URL: "{}" \
                Rererrer URL: {}""", canURL, reference);
            return false;
        }

        if (normalizedCanURL.equals(reference)) {
            LOG.debug("Canonical URL detected is the same as document "
                  + "URL. Process normally. URL: {}", reference);
            return true;
        }

        // if circling back here again, we are in a loop, process
        // it regardless
        if (docRec.getRedirectTrail().contains(normalizedCanURL)) {
            if (LOG.isWarnEnabled()) {
                LOG.warn("""
                    Circular reference between redirect and canonical\s\
                    URL detected. Will ignore canonical directive and\s\
                    process URL: "{}". Redirect trail: {}""", reference,
                    Arrays.toString(docRec.getRedirectTrail().toArray()));
            }
            return true;
        }

        var newRecord = new WebDocRecord(docRec);
        newRecord.setReference(canURL);
        newRecord.setReferrerReference(reference);

        if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                docRec.getReference(), canURL)) {
            // Call Queue pipeline on Canonical URL
            LOG.debug("""
                Canonical URL detected is different than\s\
                document URL. Document will be rejected while\s\
                canonical URL will be queued for processing: {}""",
                canURL);

            ctx.getCrawler().queueDocRecord(newRecord);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Canonical URL not in scope: {}", canURL);
            }
            newRecord.setState(CrawlDocState.REJECTED);
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_FILTER)
                    .source(ctx.getCrawler())
                    .subject(ctx.getConfig().getURLCrawlScopeStrategy())
                    .crawlDocRecord(newRecord)
                    .build());
        }

        docRec.setState(CrawlDocState.REJECTED);
        ctx.fire(CrawlerEvent.builder()
                .name(WebCrawlerEvent.REJECTED_NONCANONICAL)
                .source(ctx.getCrawler())
                .subject(detector)
                .crawlDocRecord(docRec)
                .message(detector.getClass().getSimpleName()
                        + "[canonical=" + canURL + "]")
                .build());
        return false;
    }
}