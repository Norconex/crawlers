/* Copyright 2010-2025 Norconex Inc.
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

import com.norconex.crawler.core.doc.CrawlDocStage;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.importer.ImporterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
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
            String redirectTargetURL) {

        var ctx = (WebImporterPipelineContext) context;
        var docLedger = ctx.getCrawlerContext().getDocLedger();
        var docContext = (WebCrawlDocContext) ctx.getDoc().getDocContext();
        String sourceURL = docContext.getReference();
        docContext.setRedirectTarget(redirectTargetURL);

        var requeue = false;

        //-- Fired rejected redirected event ---
        docContext.setState(WebCrawlDocStatus.REDIRECT);

        var newResponse = HttpClientFetchResponse.builder()
                .resolutionStatus(WebCrawlDocStatus.REDIRECT)
                .reasonPhrase(
                        response.getReasonPhrase()
                                + " (target: " + redirectTargetURL + ")")
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
        var redirectUrlTargetStage =
                docLedger.getStage(redirectTargetURL);

        //TODO throw an event if already active/processed(ing)?
        if (CrawlDocStage.QUEUED.is(redirectUrlTargetStage)) {
            rejectRedirectDup("queued", sourceURL, redirectTargetURL);
            return;
        }
        if (CrawlDocStage.RESOLVED.is(redirectUrlTargetStage)
                || CrawlDocStage.UNRESOLVED.is(redirectUrlTargetStage)) {
            // If part of redirect trail, allow a second queueing
            // but not more.  This in case redirecting back to self is
            // part of a normal flow (e.g. weird login).
            // If already queued twice, we treat as a loop
            // and we reject.
            if (docContext.getRedirectTrail().contains(redirectTargetURL)) {
                LOG.trace("Redirect encountered for 3rd time, rejecting: {}",
                        redirectTargetURL);
                rejectRedirectDup("processed", sourceURL, redirectTargetURL);
                return;
            }

            // If redirect is already processed with a good state, do not queue
            // it again and leave it there.
            // XXX use a memory cache of X processed with good state if
            // XXX getting performance issues.  We can't rely on pre-loaded
            // XXX cached instance, since it is pre-loaded with the source
            // XXX URL, and not the redirect URL. So we load it here.
            var op = docLedger.getProcessed(redirectTargetURL);
            if (op.isPresent()) {
                if (op.get().getState().isGoodState()) {
                    LOG.trace(
                            "Redirect URL was previously processed and "
                                    + "is valid, rejecting: {}",
                            redirectTargetURL);
                    rejectRedirectDup("processed", sourceURL,
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
        var newRec = new WebCrawlDocContext(
                redirectTargetURL, docContext.getDepth());
        newRec.setReferrerReference(docContext.getReferrerReference());
        newRec.setReferrerLinkMetadata(docContext.getReferrerLinkMetadata());
        newRec.setRedirectTrail(docContext.getRedirectTrail());
        newRec.addRedirectURL(sourceURL);
        if (requeue) {
            docLedger.queue(newRec);
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
            LOG.debug("URL redirect target not in scope: {}",
                    redirectTargetURL);
            newRec.setState(CrawlDocStatus.REJECTED);
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
