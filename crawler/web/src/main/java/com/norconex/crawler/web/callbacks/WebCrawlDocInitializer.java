/* Copyright 2023-2024 Norconex Inc.
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

import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BiConsumer;

import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocMetadata;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.WebDocMetadata;

/**
 * Initialize a Web CrawlDoc.
 */
class WebCrawlDocInitializer
        implements BiConsumer<CrawlerContext, CrawlDoc> {

    @Override
    public void accept(CrawlerContext crawler, CrawlDoc doc) {
        var docRecord = (WebCrawlDocContext) doc.getDocContext();
        var cachedDocRecord = (WebCrawlDocContext) doc.getCachedDocContext();
        var metadata = doc.getMetadata();

        //TODO consider moving metadata setting elsewhere
        // (and use reflextion?)

        //TODO should DEPTH be set here now that is is in Core?
        metadata.add(CrawlDocMetadata.DEPTH, docRecord.getDepth());
        metadata.add(
                WebDocMetadata.SM_CHANGE_FREQ,
                docRecord.getSitemapChangeFreq());
        metadata.add(WebDocMetadata.SM_LASTMOD, docRecord.getSitemapLastMod());
        metadata.add(
                WebDocMetadata.SM_PRORITY,
                docRecord.getSitemapPriority());

        // In case the crawl data supplied is from a URL that was pulled
        // from cache because the parent was skipped and could not be
        // extracted normally with link information, we attach referrer
        // data here if null
        // (but only if referrer reference is not null, which should never
        // be in this case as it is set by beforeFinalizeDocumentProcessing()
        // below.
        // We do not need to do this for sitemap information since the queue
        // pipeline takes care of (re)adding it.
        if (cachedDocRecord != null
                && docRecord.getReferrerReference() != null
                && Objects.equals(
                        docRecord.getReferrerReference(),
                        cachedDocRecord.getReferrerReference())
                && (docRecord.getReferrerLinkMetadata() == null)) {
            docRecord.setReferrerLinkMetadata(
                    cachedDocRecord.getReferrerLinkMetadata());
        }

        // Add referrer data to metadata
        //TODO move elsewhere, like .core?
        metadata.add(
                WebDocMetadata.REFERRER_REFERENCE,
                docRecord.getReferrerReference());
        if (docRecord.getReferrerLinkMetadata() != null) {
            var linkMeta = new Properties();
            linkMeta.fromString(docRecord.getReferrerLinkMetadata());
            for (Entry<String, List<String>> en : linkMeta.entrySet()) {
                var key = WebDocMetadata.REFERRER_LINK_PREFIX + en.getKey();
                for (String value : en.getValue()) {
                    if (value != null) {
                        metadata.add(key, value);
                    }
                }
            }
        }

        // Add possible redirect trail
        if (!docRecord.getRedirectTrail().isEmpty()) {
            metadata.setList(
                    WebDocMetadata.REDIRECT_TRAIL,
                    docRecord.getRedirectTrail());
        }
    }
}
