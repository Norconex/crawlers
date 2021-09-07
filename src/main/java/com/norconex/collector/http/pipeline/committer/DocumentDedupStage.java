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
package com.norconex.collector.http.pipeline.committer;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.core.doc.CrawlState;
import com.norconex.collector.core.store.IDataStore;
import com.norconex.collector.http.doc.HttpDocInfo;

/**
 * Provided both document checksum and deduplication are enabled,
 * verify there are no other documents with the same content checksum.
 * This is not 100% fool-proof due to concurrency but should capture
 * the vast majority of duplicates.
 * @author Pascal Essiembre
 */
class DocumentDedupStage extends AbstractCommitterStage {

    private static final Logger LOG =
            LoggerFactory.getLogger(DocumentDedupStage.class);


    @Override
    public boolean executeStage(HttpCommitterPipelineContext ctx) {

        HttpDocInfo docInfo = ctx.getDocInfo();

        IDataStore<String> store = ctx.getCrawler().getDedupDocumentStore();
        if (store == null || docInfo.getContentChecksum() == null) {
            return true;
        }

        Optional<String> ref = store.find(docInfo.getContentChecksum());

        if (ref.isPresent()) {

            if (LOG.isDebugEnabled()) {
                LOG.debug("REJECTED duplicate content checkum found for: {}",
                        docInfo.getReference());
            }
            docInfo.setState(CrawlState.REJECTED);
            ctx.fire(CrawlerEvent.REJECTED_DUPLICATE, b -> b
                    .crawlDocInfo(ctx.getDocInfo())
                    .subject(ref.get())
                    .message("A document with the same content checksum "
                            + "was already processed: " + ref.get()));
            return false;
        }

        store.save(docInfo.getContentChecksum(), docInfo.getReference());
        return true;
    }
}