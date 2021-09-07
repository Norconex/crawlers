/* Copyright 2021 Norconex Inc.
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

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.core.store.IDataStore;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.collector.http.fetch.HttpMethod;

/**
 * Provided both document checksum and deduplication are enabled,
 * verify there are no other documents with the same metadata checksum.
 * This is not 100% fool-proof due to concurrency but should capture
 * the vast majority of duplicates.
 * @author Pascal Essiembre
 */
class MetadataDedupStage extends AbstractHttpMethodStage {

    private static final Logger LOG =
            LoggerFactory.getLogger(MetadataDedupStage.class);

    public MetadataDedupStage(HttpMethod method) {
        super(method);
    }

    @Override
    public boolean executeStage(
            HttpImporterPipelineContext ctx, HttpMethod method) {

        if (wasHttpHeadPerformed(ctx)) {
            return true;
        }

        HttpDocInfo docInfo = ctx.getDocInfo();
        IDataStore<String> store = ctx.getCrawler().getDedupMetadataStore();
        if (store == null || docInfo.getMetaChecksum() == null) {
            return true;
        }

        Optional<String> ref = store.find(docInfo.getMetaChecksum());

        if (ref.isPresent()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED duplicate metadata checkum found for: {}",
                        docInfo.getReference());
            }
            docInfo.setState(CrawlState.REJECTED);
            ctx.fire(CrawlerEvent.REJECTED_DUPLICATE, b -> b
                    .crawlDocInfo(ctx.getDocInfo())
                    .subject(ref.get())
                    .message("A document with the same metadata checksum "
                            + "was already processed: " + ref.get()));
            return false;
        }

        store.save(docInfo.getMetaChecksum(), docInfo.getReference());
        return true;
    }
}