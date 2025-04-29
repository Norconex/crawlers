/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.doc.pipelines;

import java.io.Closeable;
import java.util.Optional;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDocContext;
import com.norconex.grid.core.storage.GridMap;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DedupService implements Closeable {

    //TODO merge with CrawlDocLedger if we can query by checksum
    // at no extra cost?

    private GridMap<String> dedupMetadataStore; // checksum -> ref
    private GridMap<String> dedupDocumentStore; // checksum -> ref

    private boolean initialized;

    public void init(CrawlerContext crawler) {
        if (initialized) {
            throw new IllegalStateException("Already initialized.");
        }
        var storeEngine = crawler.getGrid().getStorage();
        var config = crawler.getConfiguration();

        // only enable if configured to do dedup
        if (config.isMetadataDeduplicate()
                && config.getMetadataChecksummer() != null) {
            dedupMetadataStore =
                    storeEngine.getMap("dedupMetadata", String.class);
            LOG.info("Initialized deduplication based on document metadata.");
        }
        if (config.isDocumentDeduplicate()
                && config.getDocumentChecksummer() != null) {
            dedupDocumentStore =
                    storeEngine.getMap("dedupDocument", String.class);
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
    public Optional<String>
            findOrTrackDocument(CrawlDocContext docContext) {
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
    public Optional<String>
            findOrTrackMetadata(CrawlDocContext docContext) {
        return doFindOrTrack(
                dedupMetadataStore,
                docContext.getMetaChecksum(),
                docContext.getReference());
    }

    private Optional<String> doFindOrTrack(
            GridMap<String> store, String checksum, String reference) {

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
        initialized = false;
    }
}
