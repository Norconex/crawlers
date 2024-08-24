/* Copyright 2020-2024 Norconex Inc.
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
import com.norconex.importer.doc.DocContext;
import com.norconex.importer.handler.HandlerContext;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A crawl document, which holds an additional {@link HandlerContext} from cache
 * (if any).
 */
@EqualsAndHashCode
@ToString
public class CrawlDoc extends Doc {

    private final CrawlDocContext cachedDocContext;

    // maybe move this to context or create new QueueDocType
    // (regular, orphans_delete, orphans_reprocess)?
    private final boolean orphan;

    //TODO try with this?
//    private boolean deleted;

    /**
     * Creates a new crawl document with an empty input stream.
     * @param docContext document context
     * @since 4.0.0
     */
    public CrawlDoc(DocContext docContext) {
        this(docContext, null, CachedInputStream.nullInputStream(), false);
    }
    public CrawlDoc(DocContext docContext, CachedInputStream content) {
        this(docContext, null, content, false);
    }
    public CrawlDoc(
            DocContext docContext,
            CrawlDocContext cachedDocContext,
            CachedInputStream content) {
        this(docContext, cachedDocContext, content, false);
    }
    public CrawlDoc(
            DocContext docContext,
            CrawlDocContext cachedDocContext,
            CachedInputStream content,
            boolean orphan) {
        super(docContext, content, null);
        this.cachedDocContext = cachedDocContext;
        this.orphan = orphan;
    }

    @Override
    public CrawlDocContext getDocContext() {
        return (CrawlDocContext) super.getDocContext();
    }

    public boolean isOrphan() {
        return orphan;
    }

    public CrawlDocContext getCachedDocContext() {
        return cachedDocContext;
    }

    public boolean hasCache() {
        return cachedDocContext != null;
    }
}
