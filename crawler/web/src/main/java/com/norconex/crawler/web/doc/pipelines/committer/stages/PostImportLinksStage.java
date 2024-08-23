/* Copyright 2020-2023 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.committer.stages;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.doc.pipelines.committer.CommitterPipelineContext;
import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.event.WebCrawlerEvent;
import com.norconex.crawler.web.util.Web;

import lombok.extern.slf4j.Slf4j;

/**
 * Queue for crawling URLs from matching metadata fields, if any.
 * @since 3.0.0
 */
@Slf4j
public class PostImportLinksStage
        implements Predicate<CommitterPipelineContext> {

    @Override
    public boolean test(CommitterPipelineContext ctx) {
        var cfg = Web.config(ctx.getCrawler());

        var fieldMatcher = cfg.getPostImportLinks();
        if (StringUtils.isBlank(fieldMatcher.getPattern())) {
            return true;
        }

        var doc = ctx.getDoc();
        var fieldsWithLinks = doc.getMetadata().matchKeys(fieldMatcher);
        if (fieldsWithLinks.isEmpty()) {
            return true;
        }

        var docRecord = (WebCrawlDocContext) ctx.getDoc().getDocContext();

        // Previously extracted URLs.
        Set<String> extractedURLs =
                new HashSet<>(docRecord.getReferencedUrls());

        // Reject new URLs from post-import that were previously extracted
        Set<String> postImportURLs = new HashSet<>(fieldsWithLinks.valueList());
        postImportURLs.removeAll(extractedURLs);

        Set<String> inScopeUrls = new HashSet<>();

        for (String url : postImportURLs) {
            handlePostImportLink(ctx, inScopeUrls, url);
        }

        // if not keeping, delete matching fields
        if (!cfg.isPostImportLinksKeep()) {
            doc.getMetadata().keySet().removeAll(fieldsWithLinks.keySet());
        }

        extractedURLs.addAll(inScopeUrls);
        docRecord.setReferencedUrls(new ArrayList<>(extractedURLs));

        ctx.getCrawler().fire(
                CrawlerEvent.builder()
                .name(WebCrawlerEvent.URLS_POST_IMPORTED)
                .source(ctx.getCrawler())
                .subject(inScopeUrls)
                .docContext(ctx.getDoc().getDocContext())
                .build());
        return true;
    }

    private void handlePostImportLink(
            CommitterPipelineContext ctx, Set<String> inScopeUrls, String url) {

        var cfg = Web.config(ctx.getCrawler());
        var doc = ctx.getDoc();
        var docRecord = (WebCrawlDocContext) ctx.getDoc().getDocContext();

        try {
            var scopedUrlCtx = new WebCrawlDocContext(url);
            var urlScope = cfg.getUrlScopeResolver().resolve(
                    doc.getReference(), scopedUrlCtx);
            Web.fireIfUrlOutOfScope(ctx.getCrawler(), scopedUrlCtx, urlScope);
            if (urlScope.isInScope()) {
                LOG.trace("Post-import URL in crawl scope: {}", url);
                // only queue if not queued already for this doc
                if (inScopeUrls.add(url)) {
                    var newDocRec = new WebCrawlDocContext(
                            url, docRecord.getDepth() + 1);
                    newDocRec.setReferrerReference(doc.getReference());
                    ctx.getCrawler()
                    .getDocPipelines()
                    .getQueuePipeline()
                    .accept(new QueuePipelineContext(
                            ctx.getCrawler(), docRecord));
                    String afterQueueURL = newDocRec.getReference();
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