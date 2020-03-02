/* Copyright 2020 Norconex Inc.
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
package com.norconex.collector.http.fetch.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.doc.HttpDocInfo;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.doc.DocInfo;

/**
 * Utility methods for fetcher implementations using Apache HttpClient.
 * @author Pascal Essiembre
 * @since 3.0.0
 */
public final class ApacheHttpUtil {

    private ApacheHttpUtil() {
        super();
    }

    /**
     * <p>
     * Applies the HTTP response content to a document if such content exists.
     * The stream is fully downloaded and associated with a document.
     * </p>
     * @param response the HTTP response
     * @param doc document to apply headers on
     * @return <code>true</code> if there was content to apply
     * @throws IOException could not read existing content
     */
    public static boolean applyResponseContent(
            HttpResponse response, CrawlDoc doc) throws IOException {

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            // just in case...
            EntityUtils.consumeQuietly(entity);
            return false;
        }
        try (CachedInputStream content =
                doc.getStreamFactory().newInputStream(entity.getContent())) {
            content.enforceFullCaching();
            doc.setInputStream(content);
        } finally {
            // just in case...
            EntityUtils.consumeQuietly(entity);
        }
        return true;
    }

    /**
     * <p>
     * Applies the HTTP response headers to a document. This method will
     * do its best to derive relevant information from the HTTP headers
     * that can be set on the document {@link DocInfo}:
     * </p>
     * <ul>
     *   <li>Content type</li>
     *   <li>Content encoding</li>
     *   <!--<li>Document checksum</li>-->
     * </ul>
     * <p>
     * In addition, all HTTP headers will be added to the document metadata,
     * with an optional prefix.
     * </p>
     * @param response the HTTP response
     * @param prefix optional metadata prefix for all HTTP response headers
     * @param doc document to apply headers on
     */
    public static void applyResponseHeaders(
            HttpResponse response, String prefix, CrawlDoc doc) {
        HttpDocInfo docInfo = (HttpDocInfo) doc.getDocInfo();
        HeaderIterator it = response.headerIterator();
        while (it.hasNext()) {
            Header header = (Header) it.next();
            String name = header.getName();
            String value = header.getValue();

            if (StringUtils.isBlank(value)) {
                continue;
            }

            // Content-Type + Content Encoding (Charset)
            if (HttpHeaders.CONTENT_TYPE.equalsIgnoreCase(name)) {
                // delegate parsing of content-type honoring various forms
                // https://tools.ietf.org/html/rfc7231#section-3.1.1
                org.apache.http.entity.ContentType apacheCT =
                        org.apache.http.entity.ContentType.parse(value);

                // only overwrite object properties if not null
                ContentType ct = ContentType.valueOf(apacheCT.getMimeType());
                if (ct != null) {
                    docInfo.setContentType(ct);
                }
                Charset charset = apacheCT.getCharset();
                if (charset != null) {
                    docInfo.setContentEncoding(charset.toString());
                }
            }

//Have people grab it from metadata if they want to use it for checksum
//            // MD5 Checksum
//            if (HttpHeaders.CONTENT_MD5.equalsIgnoreCase(name)) {
//                docInfo.setMetaChecksum(value);
//            }

            if (StringUtils.isNotBlank(prefix)) {
                name = prefix + name;
            }
            PropertySetter.OPTIONAL.apply(doc.getMetadata(), name, value);
        }
    }

    /**
     * Sets the <code>If-Modified-Since</code> HTTP requeset header based
     * on document last crawled date.
     * @param request HTTP request
     * @param doc document
     */
    public static void setRequestIfModifiedSince(
            HttpRequest request, CrawlDoc doc) {
        if (doc.hasCache()) {
            ZonedDateTime zdt = doc.getCachedDocInfo().getCrawlDate();
            if (zdt != null) {
                request.addHeader(HttpHeaders.IF_MODIFIED_SINCE,
                        zdt.format(DateTimeFormatter.RFC_1123_DATE_TIME));
            }
        }
    }
}
