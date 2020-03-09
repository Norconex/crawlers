/* Copyright 2010-2019 Norconex Inc.
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

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.data.HttpCrawlData;
import com.norconex.collector.http.data.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.fetch.HttpFetchResponse;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.collector.http.url.ICanonicalLinkDetector;
import com.norconex.collector.http.url.IURLNormalizer;
import com.norconex.commons.lang.file.ContentType;

/**
 * @author Pascal Essiembre
 */
/*default*/ final class HttpImporterPipelineUtil {

    private static final Logger LOG =
            LogManager.getLogger(HttpImporterPipelineUtil.class);

    /**
     * Constructor.
     */
    private HttpImporterPipelineUtil() {
    }

    //TODO consider making public, putting content type and encoding in CORE.
    public static void applyMetadataToDocument(HttpDocument doc) {
        if (doc.getContentType() == null) {
            doc.setContentType(ContentType.valueOf(
                    doc.getMetadata().getString(
                            HttpMetadata.COLLECTOR_CONTENT_TYPE)));
            doc.setContentEncoding(doc.getMetadata().getString(
                    HttpMetadata.COLLECTOR_CONTENT_ENCODING));
        }
    }

    public static void enhanceHTTPHeaders(HttpMetadata meta) {
        String colCT = meta.getString(HttpMetadata.COLLECTOR_CONTENT_TYPE);
        String colCE = meta.getString(HttpMetadata.COLLECTOR_CONTENT_ENCODING);

        if (StringUtils.isNotBlank(colCT) && StringUtils.isNotBlank(colCE)) {
            return;
        }

        // Grab content type from HTTP Header
        String httpCT = meta.getString(HttpMetadata.HTTP_CONTENT_TYPE);
        if (StringUtils.isBlank(httpCT)) {
            for (String key : meta.keySet()) {
                if (StringUtils.endsWith(key, HttpMetadata.HTTP_CONTENT_TYPE)) {
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
                    meta.addString(HttpMetadata.COLLECTOR_CONTENT_TYPE, ct);
                }
            }

            if (StringUtils.isBlank(colCE)) {
                // Grab charset form HTTP Content-Type
                String ce = Objects.toString(parsedCT.getCharset(), null);
                if (ce != null) {
                    meta.addString(HttpMetadata.COLLECTOR_CONTENT_ENCODING, ce);
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
        HttpCrawlData crawlData = ctx.getCrawlData();
        String reference = crawlData.getReference();

        String canURL = null;
        if (fromMeta) {
            // Proceed with metadata (HTTP headers) canonical link detection
            canURL = detector.detectFromMetadata(reference, ctx.getMetadata());
        } else {
            // Proceed with document (<meta>) canonical link detection
            try {
                canURL = detector.detectFromContent(
                        reference,
                        ctx.getDocument().getContent(),
                        ctx.getDocument().getContentType());
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
                      + "will be processed instead.  Canonical URL: \""
                      +  canURL + "\" Rererrer URL: " + reference);
                return false;
            }

            if (normalizedCanURL.equals(reference)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Canonical URL detected is the same as document "
                          + "URL. Process normally. URL: " + reference);
                }
                return true;
            }

            // if circling back here again, we are in a loop, process
            // it regardless
            if (ArrayUtils.contains(
                    crawlData.getRedirectTrail(), normalizedCanURL)) {
                LOG.warn("Circular reference between redirect and canonical "
                      + "URL detected. Will ignore canonical directive and "
                      + "process URL: \"" + reference + "\". Redirect trail: "
                      + Arrays.toString(crawlData.getRedirectTrail()));
                return true;
            }



            HttpCrawlData newData = (HttpCrawlData) crawlData.clone();
            newData.setReference(canURL);
            newData.setReferrerReference(reference);

            if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                    crawlData.getReference(), canURL)) {
                // Call Queue pipeline on Canonical URL
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Canonical URL detected is different than "
                        + "document URL. Document will be rejected while "
                        + "canonical URL will be queued for processing: "
                        + canURL);
                }

                HttpQueuePipelineContext newContext =
                        new HttpQueuePipelineContext(
                                ctx.getCrawler(),
                                ctx.getCrawlDataStore(),
                                newData);
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

            crawlData.setState(HttpCrawlState.REJECTED);
            ctx.getCrawler().fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_NONCANONICAL,
                    crawlData, detector);
            return false;
        }
        return true;
    }

    // Keep this method static so multi-threads treat this method as one
    // instance (to avoid redirect dups).
    public static synchronized void queueRedirectURL(
            HttpImporterPipelineContext ctx,
            HttpFetchResponse response,
            String redirectURL) {
        ICrawlDataStore store = ctx.getCrawlDataStore();
        HttpCrawlData crawlData = ctx.getCrawlData();
        String sourceURL =  crawlData.getReference();

        //--- Reject source URL ---
        crawlData.setState(HttpCrawlState.REDIRECT);
        HttpFetchResponse newResponse = new HttpFetchResponse(
                HttpCrawlState.REDIRECT,
                response.getStatusCode(),
                response.getReasonPhrase() + " (" + redirectURL + ")");
        ctx.fireCrawlerEvent(HttpCrawlerEvent.REJECTED_REDIRECTED,
                crawlData, newResponse);

        boolean requeue = false;

        //--- Do not queue target URL if previously handled ---
        //TODO throw an event if already active/processed(ing)?
        if (store.isActive(redirectURL)) {
            logRedirectTargetAlreadyHandled("being processed", sourceURL, redirectURL);
            return;
        } else if (store.isQueued(redirectURL)) {
            logRedirectTargetAlreadyHandled("queued", sourceURL, redirectURL);
            return;
        } else if (store.isProcessed(redirectURL)) {
            // If part of redirect trail, allow a second queueing
            // but not more.  This in case redirecting back to self is
            // part of a normal flow (e.g. weird login).
            // If already queued twice, we treat as a loop
            // and we reject.
            if (ArrayUtils.contains(
                    crawlData.getRedirectTrail(), redirectURL)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Redirect encountered for 3rd time, rejecting: "
                        + redirectURL);
                }
                logRedirectTargetAlreadyHandled("processed", sourceURL, redirectURL);
                return;
            //TODO improve this #533 hack in v3
            } else if (HttpImporterPipeline.GOOD_REDIRECTS.contains(
                    redirectURL)) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Redirect URL previously processed and was "
                            + "valid, rejecting: " + redirectURL);
                }
                logRedirectTargetAlreadyHandled("processed", sourceURL, redirectURL);
                return;
            }

            requeue = true;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Redirect URL encountered a second time, re-queue it "
                        + "again (once) in case it came from a circular "
                        + "reference: " + redirectURL);
            }
        }

        //--- Fresh URL, queue it! ---

        HttpCrawlData newData = new HttpCrawlData(
                redirectURL, crawlData.getDepth());
        newData.setReferrerReference(crawlData.getReferrerReference());
        newData.setReferrerLinkTag(crawlData.getReferrerLinkTag());
        newData.setReferrerLinkText(crawlData.getReferrerLinkText());
        newData.setReferrerLinkTitle(crawlData.getReferrerLinkTitle());
        newData.setRedirectTrail(
                ArrayUtils.add(crawlData.getRedirectTrail(), sourceURL));
        if (requeue) {
            store.queue(newData);
        } else if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                crawlData.getReference(), redirectURL)) {

//            //TODO improve this #533 hack
//            REDIRECTS.put(sourceURL, redirectURL);

            HttpQueuePipelineContext newContext =
                    new HttpQueuePipelineContext(
                            ctx.getCrawler(), store, newData);
            new HttpQueuePipeline().execute(newContext);
        } else {
            if (LOG.isDebugEnabled()) {
                LOG.debug("URL redirect target not in scope: " + redirectURL);
            }
            newData.setState(HttpCrawlState.REJECTED);
            ctx.fireCrawlerEvent(
                    HttpCrawlerEvent.REJECTED_FILTER, newData,
                    ctx.getConfig().getURLCrawlScopeStrategy());
        }
    }

    private static void logRedirectTargetAlreadyHandled(String action,
            String originalURL, String redirectURL) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Redirect target URL is already " + action
                    + ": " + redirectURL + " (from: " + originalURL + ").");
        }
    }
}
