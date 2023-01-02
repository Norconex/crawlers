/* Copyright 2020-2022 Norconex Inc.
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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocRecord;

/**
 * A crawl document, which holds an additional {@link DocRecord} from cache
 * (if any).
 */
public class CrawlDoc extends Doc {

    private final CrawlDocRecord cachedDocInfo;
    private final boolean orphan;

    public CrawlDoc(DocRecord docRecord, CachedInputStream content) {
        this(docRecord, null, content, false);
    }
    public CrawlDoc(
            DocRecord docRecord,
            CrawlDocRecord cachedDocInfo,
            CachedInputStream content) {
        this(docRecord, cachedDocInfo, content, false);
    }
    public CrawlDoc(
            DocRecord docRecord,
            CrawlDocRecord cachedDocInfo,
            CachedInputStream content, boolean orphan) {
        super(docRecord, content, null);
        this.cachedDocInfo = cachedDocInfo;
        this.orphan = orphan;
    }

    @Override
    public CrawlDocRecord getDocInfo() {
        return (CrawlDocRecord) super.getDocInfo();
    }

    public boolean isOrphan() {
        return orphan;
    }

    public CrawlDocRecord getCachedDocInfo() {
        return cachedDocInfo;
    }

    public boolean hasCache() {
        return cachedDocInfo != null;
    }

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        ReflectionToStringBuilder b = new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE);
        b.setExcludeNullValues(true);
        return b.toString();
    }
}
