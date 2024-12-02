/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.commands.crawl.task.pipelines;

import java.io.Closeable;
import java.util.Optional;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.crawler.core.grid.GridCache;

import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Tracks documents states and any other meta information required
 * for document processing. Acts as a facade over a few processing
 * state-spacific data stores. Includes persisting of information
 * necessary for incremental crawls and resumes.
 * <p>
 * <p>
 * A document has the following stages:
 * </p>
 * The few stages a reference should have in most implementations are:</p>
 * <ul>
 *   <li><b>Queued:</b> References extracted from documents are first queued for
 *       future processing.</li>
 *   <li><b>Active:</b> A reference is being processed.</li>
 *   <li><b>Processed:</b> A reference has been processed.  If the same URL is
 *       encountered again during the same run, it will be ignored.</li>
 *   <li><b>Cached:</b> When crawling is over, processed references will be
 *       cached on the next run.</li>
 * </ul>
 */
@Slf4j
public class DedupService implements Closeable {

    //TODO merge with DocTrackerService if we can query by checksum
    // at no extra cost?

    private GridCache<String> dedupMetadataStore; // checksum -> ref
    private GridCache<String> dedupDocumentStore; // checksum -> ref

    private boolean initialized;

    public void init(CrawlerContext crawler) {
        if (initialized) {
            throw new IllegalStateException("Already initialized.");
        }
        var storeEngine = crawler.getGrid().storage();
        var config = crawler.getConfiguration();

        // only enable if configured to do dedup
        if (config.isMetadataDeduplicate()
                && config.getMetadataChecksummer() != null) {
            dedupMetadataStore =
                    storeEngine.getCache("dedupMetadata", String.class);
            LOG.info("Initialized deduplication based on document metadata.");
        }
        if (config.isDocumentDeduplicate()
                && config.getDocumentChecksummer() != null) {
            dedupDocumentStore =
                    storeEngine.getCache("dedupDocument", String.class);
            LOG.info("Initialized deduplication based on document content.");
        }
    }

    /**
     * Finds a document with the same checksum as the one supplied (effectively
     * being a duplicate) and return its reference. Otherwise save the
     * checksum of the supplied document and return an empty optional.
     * @param docContext doc to check for duplicates
     * @return the duplicate reference or empty
     */
    public Optional<String> findOrTrackDocument(CrawlDocContext docContext) {
        return doFindOrTrack(
                dedupDocumentStore,
                docContext.getContentChecksum(),
                docContext.getReference());
    }

    /**
     * Finds a document with the same checksum as the one supplied (effectively
     * being a duplicate) and return its reference. Otherwise save the
     * checksum of the supplied document and return an empty optional.
     * @param docContext doc to check for duplicates
     * @return the duplicate reference or empty
     */
    public Optional<String> findOrTrackMetadata(CrawlDocContext docContext) {
        return doFindOrTrack(
                dedupMetadataStore,
                docContext.getMetaChecksum(),
                docContext.getReference());
    }

    private Optional<String> doFindOrTrack(
            GridCache<String> store, String checksum, String reference) {
        if (store == null || checksum == null) {
            return Optional.empty();
        }
        var ref = store.get(checksum);
        if (ref == null) {
            store.put(checksum, reference);
        }
        return Optional.ofNullable(ref);
    }

    @Override
    public void close() {
        //        try {
        //            ofNullable(dedupDocumentStore).ifPresent(DataStore::close);
        //            ofNullable(dedupMetadataStore).ifPresent(DataStore::close);
        //        } finally {
        initialized = false;
        //        }
    }
}
