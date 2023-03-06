/* Copyright 2021-2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.committer;

import java.util.function.Predicate;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.core.pipeline.DocumentPipelineContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Provided both document checksum and deduplication are enabled,
 * verify there are no other documents with the same content checksum
 * encountered in the same crawl session.
 * This is not 100% fool-proof due to concurrency but should capture
 * the vast majority of duplicates.
 */
@Slf4j
class DocumentDedupStage implements Predicate<DocumentPipelineContext> {

    @Override
    public boolean test(DocumentPipelineContext ctx) {
        var docRecord = ctx.getDocRecord();

        var store = ctx.getCrawler().getDedupDocumentStore();
        if (store == null || docRecord.getContentChecksum() == null) {
            return true;
        }

        var ref = store.find(docRecord.getContentChecksum());

        if (ref.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED duplicate content checkum found for: {}",
                        docRecord.getReference());
            }
            docRecord.setState(CrawlDocState.REJECTED);
            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_DUPLICATE)
                    .source(ctx.getCrawler())
                    .subject(ref.get())
                    .crawlDocRecord(ctx.getDocRecord())
                    .message("A document with the same content checksum "
                            + "was already processed: " + ref.get())
                    .build());
            return false;
        }

        store.save(docRecord.getContentChecksum(), docRecord.getReference());
        return true;
    }
}