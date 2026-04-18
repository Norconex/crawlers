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
package com.norconex.crawler.web.stubs;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;

import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetaConstants;

public final class CrawlDocStubs {

    public static final String CRAWLDOC_CONTENT = "Some content.";
    public static final String CRAWLDOC_CONTENT_MD5 =
            "b8ab309a6b9a3f448092a136afa8fa25";

    private CrawlDocStubs() {
    }

    public static Doc crawlDoc(String ref) {
        return crawlDoc(ref, CRAWLDOC_CONTENT, new Object[] {});
    }

    public static Doc crawlDoc(String ref, String content) {
        return crawlDoc(ref, content, new Object[] {});
    }

    public static Doc crawlDoc(
            String ref, String content, Object... metaKeyValues) {
        @SuppressWarnings("resource")
        var doc = new Doc(ref).setInputStream(
                new CachedStreamFactory().newInputStream(
                        IOUtils.toInputStream(content, UTF_8)));
        if (ArrayUtils.isNotEmpty(metaKeyValues)) {
            doc.getMetadata().loadFromMap(MapUtil.toMap(metaKeyValues));
        }
        return doc;
    }

    /**
     * Creates a document stub that simulates a previously-crawled document
     * (i.e., the same reference processed a second time).
     * The "cache" concept has been replaced by the crawl entry ledger in the
     * new API; this method simply returns a {@link Doc} with the given content.
     * @param ref doc reference
     * @param content doc content
     * @param metaKeyValues doc metadata key and values
     * @return document
     */
    public static Doc crawlDocWithCache(
            String ref, String content, Object... metaKeyValues) {
        return crawlDoc(ref, content, metaKeyValues);
    }

    public static Doc crawlDocHtml(String ref) {
        return crawlDocHtml(ref, "Sample HTML content.");
    }

    public static Doc crawlDocHtml(String ref, String content) {
        return crawlDoc(
                ref, ContentType.HTML,
                IOUtils.toInputStream(content, UTF_8));
    }

    public static Doc crawlDoc(
            String ref, ContentType ct, InputStream is) {
        @SuppressWarnings("resource")
        var doc = new Doc(ref).setInputStream(CachedInputStream.cache(is));
        doc.setContentType(ct);
        doc.getMetadata().set(DocMetaConstants.CONTENT_TYPE, ct);
        return doc;
    }
}
