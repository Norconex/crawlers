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

import java.util.Optional;

import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DedupService {

    //TODO merge with CrawlDocLedger if we can query by checksum
    // at no extra cost?

    private Cache<String> dedupMetadataStore; // checksum -> ref
    private Cache<String> dedupDocumentStore; // checksum -> ref

    public void init(CrawlSession session) {
        var ctx = session.getCrawlContext();
        var crawlConfig = ctx.getCrawlConfig();
        var cluster = session.getCluster();
        var cacheManager = cluster.getCacheManager();

        // only enable if configured to do dedup
        if (crawlConfig.isMetadataDeduplicate()
                && crawlConfig.getMetadataChecksummer() != null) {
            dedupMetadataStore =
                    cacheManager.getCache("dedupMetadata", String.class);
            LOG.info("Initialized deduplication based on document metadata.");
        } else {
            dedupMetadataStore = null;
        }
        if (crawlConfig.isDocumentDeduplicate()
                && crawlConfig.getDocumentChecksummer() != null) {
            dedupDocumentStore =
                    cacheManager.getCache("dedupDocument", String.class);
            LOG.info("Initialized deduplication based on document content.");
        } else {
            dedupDocumentStore = null;
        }
    }

    /**
     * Finds a document with the same checksum as the one supplied (effectively
     * being a duplicate) and return its reference. Otherwise save the
     * checksum of the supplied document and return an empty optional.
     * @param crawlEntry doc to check for duplicates
     * @return the duplicate reference or empty
     */
    public Optional<String>
            findOrTrackDocument(CrawlEntry crawlEntry) {
        return doFindOrTrack(
                dedupDocumentStore,
                crawlEntry.getContentChecksum(),
                crawlEntry.getReference());
    }

    /**
     * Finds a document with the same checksum as the one supplied (effectively
     * being a duplicate) and return its reference. Otherwise save the
     * checksum of the supplied document and return an empty optional.
     * @param crawlEntry doc to check for duplicates
     * @return the duplicate reference or empty
     */
    public Optional<String> findOrTrackMetadata(CrawlEntry crawlEntry) {
        return doFindOrTrack(
                dedupMetadataStore,
                crawlEntry.getMetaChecksum(),
                crawlEntry.getReference());
    }

    private Optional<String> doFindOrTrack(
            Cache<String> cache, String checksum, String reference) {

        if (cache == null || checksum == null) {
            return Optional.empty();
        }
        var ref = cache.get(checksum);
        if (ref.isEmpty()) {
            cache.put(checksum, reference);
        }
        return ref;
    }
}
