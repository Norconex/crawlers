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
package com.norconex.crawler.web.pipelines.importer;

import com.norconex.crawler.core.doc.DocProcessingStage;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.WebCrawlDocStatus;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetchResponse;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class WebImporterPipelineUtil {

    private WebImporterPipelineUtil() {
    }

    // Synchronized to avoid redirect dups.
    public static synchronized void queueRedirectURL(
            ImporterPipelineContext context,
            HttpFetchResponse response,
            String redirectURL) {

        var ctx = (WebImporterPipelineContext) context;
        var docTracker = ctx.getCrawlerContext().getDocProcessingLedger();

        var docContext = (WebCrawlDocContext) ctx.getDoc().getDocContext();
        String sourceURL = docContext.getReference();
        //        var redirectStage = docTracker.getProcessingStage(redirectURL);
        var redirectStage = docContext.getProcessingStage();

        var requeue = false;

        //-- Fired rejected redirected event ---
        docContext.setState(WebCrawlDocStatus.REDIRECT);

        var newResponse = HttpClientFetchResponse.builder()
                .resolutionStatus(WebCrawlDocStatus.REDIRECT)
                .reasonPhrase(
                        response.getReasonPhrase()
                                + " (target: " + redirectURL + ")")
                //TODO are these method calls below needed?
                .redirectTarget(response.getRedirectTarget())
                .statusCode(response.getStatusCode())
                .userAgent(response.getUserAgent())
                .build();

        ctx.getCrawlerContext().fire(
                CrawlerEvent.builder()
                        .name(WebCrawlerEvent.REJECTED_REDIRECTED)
                        .source(ctx.getCrawlerContext())
                        .subject(newResponse)
                        .docContext(docContext)
                        .message(
                                newResponse.getStatusCode()
                                        + " " + newResponse.getReasonPhrase())
                        .build());

        //--- Do not queue if previously handled ---
        //TODO throw an event if already active/processed(ing)?
        if (DocProcessingStage.QUEUED.is(redirectStage)) {
            rejectRedirectDup("queued", sourceURL, redirectURL);
            return;
        }
        if (DocProcessingStage.RESOLVED.is(redirectStage)
                || DocProcessingStage.UNRESOLVED.is(redirectStage)) {
            // If part of redirect trail, allow a second queueing
            // but not more.  This in case redirecting back to self is
            // part of a normal flow (e.g. weird login).
            // If already queued twice, we treat as a loop
            // and we reject.
            if (docContext.getRedirectTrail().contains(redirectURL)) {
                LOG.trace(
                        "Redirect encountered for 3rd time, "
                                + "rejecting: {}",
                        redirectURL);
                rejectRedirectDup("processed", sourceURL, redirectURL);
                return;
            }

            // If redirect is already processed with a good state, do not queue
            // it again and leave it there.
            // XXX use a memory cache of X processed with good state if
            // XXX getting performance issues.  We can't rely on pre-loaded
            // XXX cached instance, since it is pre-loaded with the source
            // XXX URL, and not the redirect URL. So we load it here.
            var op = docTracker.getProcessed(redirectURL);
            if (op.isPresent()) {
                if (op.get().getState().isGoodState()) {
                    LOG.trace(
                            "Redirect URL was previously processed and "
                                    + "is valid, rejecting: {}",
                            redirectURL);
                    rejectRedirectDup("processed", sourceURL, redirectURL);
                    return;
                }
            } else {
                LOG.warn("""
                        Could not load from store the processed target\s\
                        of previously redirected URL\s\
                        (should never happen):\s""", redirectURL);
            }

            requeue = true;
            LOG.debug("""
                    Redirect URL encountered a second time, re-queue it\s\
                    again (once) in case it came from a circular\s\
                    reference: {}""", redirectURL);
        }

        //--- Fresh URL, queue it! ---
        var newRec = new WebCrawlDocContext(
                redirectURL, docContext.getDepth());
        newRec.setReferrerReference(docContext.getReferrerReference());
        newRec.setReferrerLinkMetadata(docContext.getReferrerLinkMetadata());
        newRec.setRedirectTrail(docContext.getRedirectTrail());
        newRec.addRedirectURL(sourceURL);
        if (requeue) {
            docTracker.queue(newRec);
            return;
        }

        var urlScope = Web.config(ctx.getCrawlerContext())
                .getUrlScopeResolver().resolve(
                        docContext.getReference(), newRec);
        Web.fireIfUrlOutOfScope(ctx.getCrawlerContext(), newRec, urlScope);
        if (urlScope.isInScope()) {
            ctx.getCrawlerContext()
                    .getPipelines()
                    .getQueuePipeline()
                    .accept(new QueuePipelineContext(ctx.getCrawlerContext(),
                            newRec));
        } else {
            LOG.debug("URL redirect target not in scope: {}", redirectURL);
            newRec.setState(DocResolutionStatus.REJECTED);
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
