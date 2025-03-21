/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.crawler.core.doc;

import com.norconex.importer.doc.DocMetadata;

/**
 * Metadata constants for common metadata field
 * names typically set by a collector crawler.
 * @see DocMetadata
 */
public final class CrawlDocMetadata {

    //TODO use the same prefix for both crawler and importer...
    // all "document." ? In any case, no longer make it "collector."

    public static final String PREFIX = "crawler.";

    public static final String DEPTH = PREFIX + "depth";

    // Avoid duplicating metadata under Collector when there is an equivalent
    // set by Importer
    public static final String CHECKSUM_METADATA =
            PREFIX + "checksum-metadata";
    public static final String CHECKSUM_DOC =
            PREFIX + "checksum-doc";

    //    /**
    //     * A document ACL if ACL extraction is supported.
    //    //     */
    //    public static final String COLLECTOR_ACL = PREFIX + "acl";

    /**
     * Boolean flag indicating whether a document is new to the crawler that
     * fetched it.
     * That is, a URL cache from a previous run exists and the document was
     * not found in that cache. If the crawler runs for the first time
     * or its URL cache has been deleted, this flag will always be
     * <code>true</code>.
     */
    //TODO make it a counter of the number of time it was processed
    public static final String IS_DOC_NEW =
            PREFIX + "is-doc-new";

    /**
     * Qualified name of fetcher used to fetch a document.
         */
    public static final String FETCHER = PREFIX + "fetcher";

    private CrawlDocMetadata() {
    }
}
