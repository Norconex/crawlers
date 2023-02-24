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
package com.norconex.crawler.web.pipeline.importer;

import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.core.doc.CrawlDocState;
import com.norconex.crawler.web.fetch.HttpMethod;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * Provided both document checksum and deduplication are enabled,
 * verify there are no other documents with the same metadata checksum.
 * This is not 100% fool-proof due to concurrency but should capture
 * the vast majority of duplicates.
 */
@Slf4j
class MetadataDedupStage extends AbstractHttpImporterStage {

    public MetadataDedupStage(@NonNull HttpMethod method) {
        super(method);
    }

    @Override
    boolean executeStage(HttpImporterPipelineContext ctx) {

        if (ctx.wasHttpHeadPerformed(getHttpMethod())) {
            return true;
        }

        var docRecord = ctx.getDocRecord();
        var store = ctx.getCrawler().getDedupMetadataStore();
        if (store == null || docRecord.getMetaChecksum() == null) {
            return true;
        }

        var ref = store.find(docRecord.getMetaChecksum());

        if (ref.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED duplicate metadata checkum found for: {}",
                        docRecord.getReference());
            }
            docRecord.setState(CrawlDocState.REJECTED);

            ctx.fire(CrawlerEvent.builder()
                    .name(CrawlerEvent.REJECTED_DUPLICATE)
                    .source(ctx.getCrawler())
                    .subject(ref.get())
                    .crawlDocRecord(docRecord)
                    .message("A document with the same metadata checksum "
                            + "was already processed: " + ref.get())
                    .build());
            return false;
        }

        store.save(docRecord.getMetaChecksum(), docRecord.getReference());
        return true;
    }
}