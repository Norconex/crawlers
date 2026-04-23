/* Copyright 2010-2026 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.importer;

import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.ledger.ProcessingOutcome;
import com.norconex.crawler.core.ledger.ProcessingStatus;
import com.norconex.crawler.web.doc.WebProcessingOutcome;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.fetch.WebFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetchResponse;
import com.norconex.crawler.web.ledger.WebCrawlEntry;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class WebImporterPipelineUtil {

    private WebImporterPipelineUtil() {
    }

    // Synchronized to avoid redirect dups.
    public static synchronized void queueRedirectURL(
            ImporterPipelineContext context,
            WebFetchResponse response,
            String redirectTargetURL) {

        var ctx = (WebImporterPipelineContext) context;
        var crawlSession = ctx.getCrawlSession();
        var crawlContext = crawlSession.getCrawlContext();
        var crawlEntryLedger = crawlContext.getCrawlEntryLedger();
        var docContext =
                (WebCrawlEntry) ctx.getDocContext()
                        .getCurrentCrawlEntry();
        String sourceURL = docContext.getReference();
        docContext.setRedirectTarget(redirectTargetURL);

        var requeue = false;

        //-- Fired rejected redirected event ---
        docContext.setProcessingOutcome(WebProcessingOutcome.REDIRECT);

        var newResponse = HttpClientFetchResponse.builder()
                .processingOutcome(
                        WebProcessingOutcome.REDIRECT)
                .reasonPhrase(
                        response.getReasonPhrase()
                                + " (target: "
                                + redirectTargetURL
                                + ")")
                //TODO are these method calls below needed?
                .redirectTarget(response.getRedirectTarget())
                .statusCode(response.getStatusCode())
                .userAgent(response.getUserAgent())
                .build();

        crawlSession.fire(
                CrawlerEvent.builder()
                        .name(WebCrawlerEvent.REJECTED_REDIRECTED)
                        .source(crawlSession)
                        .crawlSession(crawlSession)
                        .crawlEntry(docContext)
                        .message(
                                newResponse.getStatusCode()
                                        + " "
                                        + newResponse.getReasonPhrase())
                        .build());

        //--- Do not queue if previously handled ---
        var redirectUrlTargetStatus =
                crawlEntryLedger.getProcessingStatus(
                        redirectTargetURL);
        var existingRedirectTarget =
                crawlEntryLedger.getEntry(redirectTargetURL)
                        .orElse(null);

        //TODO throw an event if already active/processed(ing)?
        if (ProcessingStatus.QUEUED.is(redirectUrlTargetStatus)) {
            rejectRedirectDup("queued", sourceURL,
                    redirectTargetURL);
            return;
        }
        if (ProcessingStatus.PROCESSING.is(redirectUrlTargetStatus)) {
            rejectRedirectDup("processing", sourceURL,
                    redirectTargetURL);
            return;
        }
        if (ProcessingStatus.PROCESSED.is(redirectUrlTargetStatus)) {
            // If part of redirect trail, allow a second queueing
            // but not more.  This in case redirecting back to self is
            // part of a normal flow (e.g. weird login).
            // If already queued twice, we treat as a loop
            // and we reject.
            if (docContext.getRedirectTrail()
                    .contains(redirectTargetURL)) {
                LOG.trace("Redirect encountered for 3rd time, rejecting: {}",
                        redirectTargetURL);
                rejectRedirectDup("processed", sourceURL,
                        redirectTargetURL);
                return;
            }

            // If redirect is already processed with a good state, do not queue
            // it again and leave it there.
            // XXX use a memory cache of X processed with good state if
            // XXX getting performance issues.  We can't rely on pre-loaded
            // XXX cached instance, since it is pre-loaded with the source
            // XXX URL, and not the redirect URL. So we load it here.
            var op = java.util.Optional
                    .ofNullable(existingRedirectTarget);
            if (op.isPresent()) {
                var outcome = op.get().getProcessingOutcome();
                if (outcome != null && outcome.isGoodState()) {
                    LOG.trace(
                            "Redirect URL was previously processed and "
                                    + "is valid, rejecting: {}",
                            redirectTargetURL);
                    rejectRedirectDup("processed",
                            sourceURL,
                            redirectTargetURL);
                    return;
                }
            } else {
                LOG.warn("""
                        Could not load from store the processed target\s\
                        of previously redirected URL\s\
                        (should never happen):\s""", redirectTargetURL);
            }

            requeue = true;
            LOG.debug("""
                    Redirect URL encountered a second time, re-queue it\s\
                    again (once) in case it came from a circular\s\
                    reference: {}""", redirectTargetURL);
        }

        //--- Fresh URL, queue it! ---
        var newRec = new WebCrawlEntry(
                redirectTargetURL, docContext.getDepth());
        newRec.setReferrerReference(docContext.getReferrerReference());
        newRec.setReferrerLinkMetadata(
                docContext.getReferrerLinkMetadata());
        newRec.setRedirectTrail(docContext.getRedirectTrail());
        newRec.addRedirectURL(sourceURL);
        if (requeue) {
            if (!crawlEntryLedger.requeueEntry(newRec)) {
                crawlEntryLedger.queue(newRec);
            }
            return;
        }

        var urlScope = Web.config(crawlContext)
                .getUrlScopeResolver().resolve(
                        docContext.getReference(),
                        newRec);
        Web.fireIfUrlOutOfScope(crawlSession, newRec, urlScope);
        if (urlScope.isInScope()) {
            crawlContext
                    .getDocPipelines()
                    .getQueuePipeline()
                    .accept(new QueuePipelineContext(
                            crawlSession,
                            newRec));
        } else {
            LOG.debug("URL redirect target not in scope: {}",
                    redirectTargetURL);
            newRec.setProcessingOutcome(ProcessingOutcome.REJECTED);
        }
    }

    private static void rejectRedirectDup(
            String action,
            String originalURL, String redirectURL) {
        LOG.debug(
                "Redirect target URL is already {}: {} (from: {}).",
                action, redirectURL, originalURL);
    }
}
