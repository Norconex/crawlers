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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlDocInfo.Stage;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpCrawlState;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpFetchResponseBuilder;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;

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

        //-- Fired rejected redirected event ---
        crawlRef.setState(HttpCrawlState.REDIRECT);
        IHttpFetchResponse newResponse = new HttpFetchResponseBuilder(response)
                .setCrawlState(HttpCrawlState.REDIRECT)
                .setReasonPhrase(response.getReasonPhrase()
                        + " (" + redirectURL + ")")
                .build();
        ctx.fireCrawlerEvent(HttpCrawlerEvent.REJECTED_REDIRECTED,
                crawlRef, newResponse);

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
                            + "valid, rejecting: {}", redirectURL);
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
        HttpDocInfo newData = new HttpDocInfo(
                redirectURL, crawlRef.getDepth());
        newData.setReferrerReference(crawlRef.getReferrerReference());
        newData.setReferrerLinkMetadata(crawlRef.getReferrerLinkMetadata());
//        newData.setReferrerLinkTag(crawlRef.getReferrerLinkTag());
//        newData.setReferrerLinkText(crawlRef.getReferrerLinkText());
//        newData.setReferrerLinkTitle(crawlRef.getReferrerLinkTitle());
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
            newData.setState(CrawlState.REJECTED);
            ctx.fireCrawlerEvent(
                    CrawlerEvent.REJECTED_FILTER, newData,
                    ctx.getConfig().getURLCrawlScopeStrategy());
        }
    }

    private static void rejectRedirectDup(String action,
            String originalURL, String redirectURL) {
        LOG.debug("Redirect target URL is already {}: {} (from: {}).",
                action, redirectURL, originalURL);
    }
}
