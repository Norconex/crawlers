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

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocRecord.Stage;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.web.crawler.HttpCrawlerEvent;
import com.norconex.crawler.web.doc.HttpCrawlDocState;
import com.norconex.crawler.web.doc.HttpDocRecord;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetchResponse;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class HttpImporterPipelineUtil {

    private HttpImporterPipelineUtil() {}

    // Synchronized to avoid redirect dups.
    public static synchronized void queueRedirectURL(
            HttpImporterPipelineContext ctx,
            HttpFetchResponse response,
            String redirectURL) {
        var crawlRef = ctx.getDocRecord();
        String sourceURL =  crawlRef.getReference();
        var redirectStage = ctx.getDocRecordService()
                .getProcessingStage(redirectURL);

        var requeue = false;

        //-- Fired rejected redirected event ---
        crawlRef.setState(HttpCrawlDocState.REDIRECT);

        var newResponse = GenericHttpFetchResponse.builder()
                .crawlDocState(HttpCrawlDocState.REDIRECT)
                .reasonPhrase(response.getReasonPhrase()
                        + " (target: " + redirectURL + ")")
                //TODO are these method calls below needed?
                .redirectTarget(response.getRedirectTarget())
                .statusCode(response.getStatusCode())
                .userAgent(response.getUserAgent())
                .build();

        ctx.fire(CrawlerEvent.builder()
                .name(HttpCrawlerEvent.REJECTED_REDIRECTED)
                .source(ctx.getCrawler())
                .subject(newResponse)
                .crawlDocRecord(crawlRef)
                .message(newResponse.getStatusCode()
                        + " " + newResponse.getReasonPhrase())
                .build());

        //--- Do not queue if previously handled ---
        //TODO throw an event if already active/processed(ing)?
        if (Stage.ACTIVE.is(redirectStage)) {
            rejectRedirectDup("being processed", sourceURL, redirectURL);
            return;
        }
        if (Stage.QUEUED.is(redirectStage)) {
            rejectRedirectDup("queued", sourceURL, redirectURL);
            return;
        }
        if (Stage.PROCESSED.is(redirectStage)) {
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
            }

            // If redirect is already processed with a good state, do not queue
            // it again and leave it there.
            // XXX use a memory cache of X processed with good state if
            // XXX getting performance issues.  We can't rely on pre-loaded
            // XXX cached instance, since it is pre-loaded with the source
            // XXX URL, and not the redirect URL. So we load it here.
            var op = ctx.getDocRecordService().getProcessed(redirectURL);
            if (op.isPresent()) {
                if (op.get().getState().isGoodState()) {
                    LOG.trace("Redirect URL was previously processed and "
                            + "is valid, rejecting: {}", redirectURL);
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
        var newRec = new HttpDocRecord(
                redirectURL, crawlRef.getDepth());
        newRec.setReferrerReference(crawlRef.getReferrerReference());
        newRec.setReferrerLinkMetadata(crawlRef.getReferrerLinkMetadata());
        newRec.setRedirectTrail(crawlRef.getRedirectTrail());
        newRec.addRedirectURL(sourceURL);
        if (requeue) {
            ctx.getDocRecordService().queue(newRec);
        } else if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                crawlRef.getReference(), redirectURL)) {
            ctx.getCrawler().queueDocRecord(newRec);
        } else {
            LOG.debug("URL redirect target not in scope: {}", redirectURL);
            newRec.setState(CrawlDocState.REJECTED);
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_FILTER)
                    .source(ctx.getCrawler())
                    .subject(ctx.getConfig().getURLCrawlScopeStrategy())
                    .crawlDocRecord(newRec)
                    .build());
        }
    }

    private static void rejectRedirectDup(String action,
            String originalURL, String redirectURL) {
        LOG.debug("Redirect target URL is already {}: {} (from: {}).",
                action, redirectURL, originalURL);
    }
}
