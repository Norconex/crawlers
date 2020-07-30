/* Copyright 2020 Norconex Inc.
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
package com.norconex.collector.http.pipeline.committer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipeline;
import com.norconex.collector.http.pipeline.queue.HttpQueuePipelineContext;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;

/**
 * Queue for crawling URLs from matching metadata fields, if any.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
/*default*/ class PostImportLinksStage extends AbstractCommitterStage {

    private static final Logger LOG =
            LoggerFactory.getLogger(PostImportLinksStage.class);

    @Override
    public boolean executeStage(HttpCommitterPipelineContext ctx) {
        TextMatcher fieldMatcher = ctx.getConfig().getPostImportLinks();
        if (StringUtils.isBlank(fieldMatcher.getPattern())) {
            return true;
        }

        CrawlDoc doc = ctx.getDocument();
        Properties fieldsWithLinks = doc.getMetadata().matchKeys(fieldMatcher);
        if (fieldsWithLinks.isEmpty()) {
            return true;
        }

        HttpDocInfo docInfo = ctx.getDocInfo();

        // Previously extracted URLs.
        Set<String> extractedURLs = new HashSet<>(docInfo.getReferencedUrls());

        // Reject new URLs from post-import that were previously extracted
        Set<String> postImportURLs = new HashSet<>(fieldsWithLinks.valueList());
        postImportURLs.removeAll(extractedURLs);

        Set<String> inScopeUrls = new HashSet<>();

        for (String url : postImportURLs) {
            handlePostImportLink(ctx, inScopeUrls, url);
        }

        // if not keeping, delete matching fields
        if (!ctx.getConfig().isPostImportLinksKeep()) {
            doc.getMetadata().keySet().removeAll(fieldsWithLinks.keySet());
        }

        extractedURLs.addAll(inScopeUrls);
        docInfo.setReferencedUrls(new ArrayList<>(extractedURLs));

        ctx.fireCrawlerEvent(HttpCrawlerEvent.URLS_POST_IMPORTED,
                ctx.getDocInfo(), inScopeUrls);
        return true;
    }

    private void handlePostImportLink(HttpCommitterPipelineContext ctx,
            Set<String> inScopeUrls, String url) {

        CrawlDoc doc = ctx.getDocument();
        HttpDocInfo docInfo = ctx.getDocInfo();

        try {
            if (ctx.getConfig().getURLCrawlScopeStrategy().isInScope(
                    doc.getReference(), url)) {
                LOG.trace("Post-import URL in crawl scope: {}", url);
                // only queue if not queued already for this doc
                if (inScopeUrls.add(url)) {
                    HttpDocInfo newURL = new HttpDocInfo(
                            url, docInfo.getDepth() + 1);
                    newURL.setReferrerReference(doc.getReference());
                    HttpQueuePipelineContext newContext =
                            new HttpQueuePipelineContext(
                                    ctx.getCrawler(), newURL);
                    new HttpQueuePipeline().execute(newContext);
                    String afterQueueURL = newURL.getReference();
                    if (!url.equals(afterQueueURL)) {
                        LOG.debug("URL modified from \"{}\" to \"{}\".",
                                url, afterQueueURL);
                    }
                    inScopeUrls.add(afterQueueURL);
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not queue post-import URL \"{}.", url, e);
        }
    }
}