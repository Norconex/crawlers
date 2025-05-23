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
package com.norconex.crawler.core.stubs;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.doc.CrawlDocContext;

public final class CrawlDocStubs {

    public static final String CRAWLDOC_CONTENT = "Some content.";
    public static final String CRAWLDOC_CONTENT_MD5 =
            "b8ab309a6b9a3f448092a136afa8fa25";

    private CrawlDocStubs() {
    }

    // Content MD5:
    public static CrawlDoc crawlDoc(String ref) {
        return crawlDoc(ref, CRAWLDOC_CONTENT, new Object[] {});
    }

    public static CrawlDoc crawlDoc(String ref, String content) {
        return crawlDoc(ref, content, new Object[] {});
    }

    public static CrawlDoc crawlDoc(
            String ref, String content, Object... metaKeyValues) {
        var doc = new CrawlDoc(
                new CrawlDocContext(ref),
                new CachedStreamFactory().newInputStream(
                        IOUtils.toInputStream(content, UTF_8)));
        if (ArrayUtils.isNotEmpty(metaKeyValues)) {
            doc.getMetadata().loadFromMap(MapUtil.toMap(metaKeyValues));
        }
        return doc;
    }

    public static CrawlDoc crawlDocWithCache(
            String ref, String content, Object... metaKeyValues) {
        var doc = new CrawlDoc(
                new CrawlDocContext(ref),
                new CrawlDocContext(ref),
                new CachedStreamFactory().newInputStream(
                        IOUtils.toInputStream(content, UTF_8)));
        if (ArrayUtils.isNotEmpty(metaKeyValues)) {
            doc.getMetadata().loadFromMap(MapUtil.toMap(metaKeyValues));
        }
        return doc;
    }
}
