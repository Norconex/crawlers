/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core2.stubs;

import com.norconex.crawler.core2.doc.CrawlDocContext;
import com.norconex.crawler.core2.ledger.CrawlEntry;

public final class CrawlDocContextStubber {
    private CrawlDocContextStubber() {
    }

    public static CrawlDocContext fresh(String ref) {
        return fresh(ref, DocStubber.CRAWLDOC_CONTENT);
    }

    // no previous entry
    public static CrawlDocContext fresh(
            String ref, String content, Object... metaKeyValues) {
        return CrawlDocContext.builder()
                .currentCrawlEntry(new CrawlEntry(ref))
                .doc(DocStubber.doc(ref, content, metaKeyValues))
                .build();
    }

    // with previous entry
    public static CrawlDocContext incremental(String ref) {
        return incremental(ref, DocStubber.CRAWLDOC_CONTENT);
    }

    public static CrawlDocContext incremental(
            String ref, String content, Object... metaKeyValues) {
        return CrawlDocContext.builder()
                .currentCrawlEntry(new CrawlEntry(ref))
                .previousCrawlEntry(new CrawlEntry(ref))
                .doc(DocStubber.doc(ref, content, metaKeyValues))
                .build();
    }
}
