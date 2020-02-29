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

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.doc.CrawlDocInfo.Stage;
import com.norconex.collector.core.doc.CrawlDocMetadata;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.url.ICanonicalLinkDetector;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.Doc;

/**
 * @author Pascal Essiembre
 */
/*default*/ final class HttpImporterPipelineUtil {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpImporterPipelineUtil.class);

    /**
     * Constructor.
     */
    private HttpImporterPipelineUtil() {
    }

    //TODO consider making public, putting content type and encoding in CORE.


    //TODO see if still needed?
    @Deprecated
    public static void applyMetadataToDocument(Doc doc) {
        if (doc.getDocInfo().getContentType() == null) {
            doc.getDocInfo().setContentType(ContentType.valueOf(
                    doc.getMetadata().getString(
                            CrawlDocMetadata.CONTENT_TYPE)));
            doc.getDocInfo().setContentEncoding(doc.getMetadata().getString(
                    CrawlDocMetadata.CONTENT_ENCODING));
        }
    }

    //TODO see if still needed?
    @Deprecated
    public static void enhanceHTTPHeaders(Properties meta) {
        String colCT = meta.getString(CrawlDocMetadata.CONTENT_TYPE);
        String colCE = meta.getString(
                CrawlDocMetadata.CONTENT_ENCODING);

        if (StringUtils.isNotBlank(colCT) && StringUtils.isNotBlank(colCE)) {
            return;
        }

        // Grab content type from HTTP Header
        String httpCT = meta.getString(HttpDocMetadata.HTTP_CONTENT_TYPE);
        if (StringUtils.isBlank(httpCT)) {
            for (String key : meta.keySet()) {
                if (StringUtils.endsWith(key, HttpDocMetadata.HTTP_CONTENT_TYPE)) {
                    httpCT = meta.getString(key);
                }
            }
        }

        if (StringUtils.isNotBlank(httpCT)) {
            // delegate parsing of content-type honoring various forms
            // https://tools.ietf.org/html/rfc7231#section-3.1.1
            org.apache.http.entity.ContentType parsedCT =
                    org.apache.http.entity.ContentType.parse(httpCT);

            if (StringUtils.isBlank(colCT)) {
                String ct = parsedCT.getMimeType();
                if (ct != null) {
                    meta.add(CrawlDocMetadata.CONTENT_TYPE, ct);
                }
            }

            if (StringUtils.isBlank(colCE)) {
                // Grab charset form HTTP Content-Type
                String ce = Objects.toString(parsedCT.getCharset(), null);
                if (ce != null) {
                    meta.add(CrawlDocMetadata.CONTENT_ENCODING, ce);
                }
            }
        }
    }

    // return true if we process this doc, false if we don't because we
    // will use a canonical URL instead
    public static boolean resolveCanonical(
            HttpImporterPipelineContext ctx, boolean fromMeta) {

        //Return right away if canonical links are ignored or no detector.
        if (ctx.getConfig().isIgnoreCanonicalLinks()
                || ctx.getConfig().getCanonicalLinkDetector() == null) {
            return true;
        }

        ICanonicalLinkDetector detector =
                ctx.getConfig().getCanonicalLinkDetector();
        HttpDocInfo crawlRef = ctx.getDocInfo();
        String reference = crawlRef.getReference();

        String canURL = null;
        if (fromMeta) {
            // Proceed with metadata (HTTP headers) canonical link detection
            canURL = detector.detectFromMetadata(reference, ctx.getMetadata());
        } else {
            // Proceed with document (<meta>) canonical link detection
            try {
                canURL = detector.detectFromContent(
                        reference,
                        ctx.getDocument().getInputStream(),
                        ctx.getDocument().getDocInfo().getContentType());
            } catch (IOException e) {
                throw new CollectorException(
                        "Cannot resolve canonical link from content for: "
                        + reference, e);
            }
        }

        if (StringUtils.isNotBlank(canURL)) {
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
                LOG.warn("Circular reference between redirect and canonical "
                      + "URL detected. Will ignore canonical directive and "
                      + "process URL: \"{}\". Redirect trail: {}", reference,
                      Arrays.toString(crawlRef.getRedirectTrail().toArray()));
                return true;
            }

            HttpDocInfo newData = new HttpDocInfo(crawlRef);
            newData.setReference(canURL);
            newData.setReferrerReference(reference);

            if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                    crawlRef.getReference(), canURL)) {
                // Call Queue pipeline on Canonical URL
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Canonical URL detected is different than "
                        + "document URL. Document will be rejected while "
                        + "canonical URL will be queued for processing: "
                        + canURL);
                }

                HttpQueuePipelineContext newContext =
                        new HttpQueuePipelineContext(ctx.getCrawler(), newData);
                new HttpQueuePipeline().execute(newContext);
            } else {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Canonical URL not in scope: " + canURL);
                }
                newData.setState(HttpCrawlState.REJECTED);
                ctx.fireCrawlerEvent(
                        HttpCrawlerEvent.REJECTED_FILTER, newData,
                        ctx.getConfig().getURLCrawlScopeStrategy());
            }

            crawlRef.setState(HttpCrawlState.REJECTED);
            ctx.fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_NONCANONICAL,
                    crawlRef, detector);
            return false;
        }
        return true;
    }

    // Keep this method static so multi-threads treat this method as one
    // instance (to avoid redirect dups).
    public static synchronized void queueRedirectURL(
            HttpImporterPipelineContext ctx,
            IHttpFetchResponse response,
            String redirectURL) {
        HttpDocInfo crawlRef = ctx.getDocInfo();
        String sourceURL =  crawlRef.getReference();
        Stage redirectStage = ctx.getDocInfoService()
                .getProcessingStage(redirectURL);

        boolean requeue = false;

        //--- Do not queue if previously handled ---
        //TODO throw an event if already active/processed(ing)?
        if (Stage.ACTIVE.is(redirectStage)) {
            rejectRedirectDup("being processed", sourceURL, redirectURL);
            return;
        } else if (Stage.QUEUED.is(redirectStage)) {
            rejectRedirectDup("queued", sourceURL, redirectURL);
            return;
        } else if (Stage.PROCESSED.is(redirectStage)) {
            // If part of redirect trail, allow a second queueing
            // but not more.  This in case redirecting back to self is
            // part of a normal flow (e.g. weird login).
            // If already queued twice, we treat as a loop
            // and we reject.
            if (crawlRef.getRedirectTrail().contains(redirectURL)) {
                LOG.trace("Redirect encountered for 3rd time, "
                        + "rejecting: {}", redirectURL);
                rejectRedirectDup("processed", sourceURL, redirectURL);
                return;
            //TODO improve this #533 hack in v3
            } else if (HttpImporterPipeline.GOOD_REDIRECTS.contains(
                    redirectURL)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Redirect URL previously processed and was "
                            + "valid, rejecting: " + redirectURL);
                }
                rejectRedirectDup("processed", sourceURL, redirectURL);
                return;
            }

            requeue = true;
            LOG.debug("Redirect URL encountered a second time, re-queue it "
                    + "again (once) in case it came from a circular "
                    + "reference: {}", redirectURL);
        }

        //--- Fresh URL, queue it! ---
        crawlRef.setState(HttpCrawlState.REDIRECT);
        //TODO instead of copying like this use builder with withXXX methods
        IHttpFetchResponse newResponse = new HttpFetchResponseBuilder(response)
                .setCrawlState(HttpCrawlState.REDIRECT)
                .setReasonPhrase(response.getReasonPhrase()
                        + " (" + redirectURL + ")")
                .build();
        ctx.fireCrawlerEvent(HttpCrawlerEvent.REJECTED_REDIRECTED,
                crawlRef, newResponse);

        HttpDocInfo newData = new HttpDocInfo(
                redirectURL, crawlRef.getDepth());
        newData.setReferrerReference(crawlRef.getReferrerReference());
        newData.setReferrerLinkTag(crawlRef.getReferrerLinkTag());
        newData.setReferrerLinkText(crawlRef.getReferrerLinkText());
        newData.setReferrerLinkTitle(crawlRef.getReferrerLinkTitle());
        newData.setRedirectTrail(crawlRef.getRedirectTrail());
        newData.addRedirectURL(sourceURL);
        if (requeue) {
            ctx.getDocInfoService().queue(newData);
        } else if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                crawlRef.getReference(), redirectURL)) {
            HttpQueuePipelineContext newContext =
                    new HttpQueuePipelineContext(ctx.getCrawler(), newData);
            new HttpQueuePipeline().execute(newContext);
        } else {
            LOG.debug("URL redirect target not in scope: {}", redirectURL);
            newData.setState(HttpCrawlState.REJECTED);
            ctx.fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_FILTER, newData,
                    ctx.getConfig().getURLCrawlScopeStrategy());
        }
    }

    private static void rejectRedirectDup(String action,
            String originalURL, String redirectURL) {
        LOG.debug("Redirect target URL is already {}: {} (from: {}).",
                action, redirectURL, originalURL);
    }
}
