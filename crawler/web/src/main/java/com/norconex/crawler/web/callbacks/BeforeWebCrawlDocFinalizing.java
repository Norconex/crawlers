/* Copyright 2023-2025 Norconex Inc.
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
package com.norconex.crawler.web.callbacks;

import java.util.function.BiConsumer;

import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocStatus;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.web.doc.WebCrawlDocContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Prepare web-document for finalization.
 */
@Slf4j
class BeforeWebCrawlDocFinalizing
        implements BiConsumer<CrawlContext, CrawlDoc> {

    @Override
    public void accept(CrawlContext crawler, CrawlDoc doc) {
        // If URLs were not yet extracted, it means no links will be followed.
        // In case the referring document was skipped or has a bad status
        // (which can always be temporary), we should queue for processing any
        // referenced links from cache to make sure an attempt will be made to
        // re-crawl these "child" links and they will not be considered orphans.
        // Else, as orphans they could wrongfully be deleted, ignored, or
        // be re-assigned the wrong depth if linked from another, deeper, page.
        // See: https://github.com/Norconex/collector-http/issues/278

        var httpData = (WebCrawlDocContext) doc.getDocContext();
        var httpCachedData = (WebCrawlDocContext) doc.getCachedDocContext();

        // If never crawled before, URLs were extracted already, or cached
        // version has no extracted, URLs, abort now.
        if (httpCachedData == null
                || !httpData.getReferencedUrls().isEmpty()
                || httpCachedData.getReferencedUrls().isEmpty()) {
            return;
        }

        // Only continue if the document could not have extracted URLs because
        // it was skipped, or in a temporary invalid state that prevents
        // accessing child links normally.
        var state = httpData.getState();
        if (!state.isSkipped() && !state.isOneOf(
                CrawlDocStatus.BAD_STATUS, CrawlDocStatus.ERROR)) {
            return;
        }

        // OK, let's do this
        LOG.debug("Queueing referenced URLs of {}", httpData.getReference());

        var childDepth = httpData.getDepth() + 1;
        var referencedUrls = httpCachedData.getReferencedUrls();
        for (String url : referencedUrls) {

            var childData = new WebCrawlDocContext(url, childDepth);
            childData.setReferrerReference(httpData.getReference());
            LOG.debug("Queueing skipped document's child: {}",
                    childData.getReference());
            crawler.getDocPipelines().getQueuePipeline().accept(
                    new QueuePipelineContext(crawler, childData));
        }
    }
}
