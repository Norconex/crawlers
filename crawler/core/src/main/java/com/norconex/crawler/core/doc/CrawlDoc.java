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
package com.norconex.crawler.core.doc;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocRecord;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A crawl document, which holds an additional {@link DocRecord} from cache
 * (if any).
 */
@EqualsAndHashCode
@ToString
public class CrawlDoc extends Doc {

    private final CrawlDocRecord cachedDocRecord;

    // maybe move this to context or create new QueueDocType
    // (regular, orphans_delete, orphans_reprocess)?
    private final boolean orphan;

    //TODO try with this?
//    private boolean deleted;

    /**
     * Creates a new crawl document with an empty input stream.
     * @param docRecord document record
     * @since 4.0.0
     */
    public CrawlDoc(DocRecord docRecord) {
        this(docRecord, null, CachedInputStream.nullInputStream(), false);
    }
    public CrawlDoc(DocRecord docRecord, CachedInputStream content) {
        this(docRecord, null, content, false);
    }
    public CrawlDoc(
            DocRecord docRecord,
            CrawlDocRecord cachedDocRecord,
            CachedInputStream content) {
        this(docRecord, cachedDocRecord, content, false);
    }
    public CrawlDoc(
            DocRecord docRecord,
            CrawlDocRecord cachedDocRecord,
            CachedInputStream content,
            boolean orphan) {
        super(docRecord, content, null);
        this.cachedDocRecord = cachedDocRecord;
        this.orphan = orphan;
    }

    @Override
    public CrawlDocRecord getDocRecord() {
        return (CrawlDocRecord) super.getDocRecord();
    }

    public boolean isOrphan() {
        return orphan;
    }

    public CrawlDocRecord getCachedDocRecord() {
        return cachedDocRecord;
    }

    public boolean hasCache() {
        return cachedDocRecord != null;
    }
}
