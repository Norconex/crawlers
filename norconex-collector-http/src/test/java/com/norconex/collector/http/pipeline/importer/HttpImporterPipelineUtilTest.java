/* Copyright 2019-2020 Norconex Inc.
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
package com.norconex.collector.http.pipeline.importer;

import static com.norconex.collector.core.doc.CrawlDocMetadata.COLLECTOR_CONTENT_ENCODING;
import static com.norconex.collector.core.doc.CrawlDocMetadata.COLLECTOR_CONTENT_TYPE;
import static com.norconex.collector.http.doc.HttpDocMetadata.HTTP_CONTENT_TYPE;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.commons.lang.map.Properties;

public class HttpImporterPipelineUtilTest {

    @Test
    public void enhanceHTTPHeaders() {

        assertEnhancedHeaders("text/html; charset=UTF-8",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html; charset=UTF-8",
                "our/custom", null,
                "our/custom", "UTF-8");

        assertEnhancedHeaders("text/html; charset=utf-8",
                null, "iso-8859-4",
                "text/html", "iso-8859-4");

        assertEnhancedHeaders("text/html; charset=",
                null, null,
                "text/html", null);

        assertEnhancedHeaders("text/html;",
                null, null,
                "text/html", null);

        assertEnhancedHeaders("text/html",
                null, null,
                "text/html", null);

        assertEnhancedHeaders("",
                null, null,
                null, null);

        assertEnhancedHeaders(null,
                null, null,
                null, null);

        // alternative allowed versions for content-type:
        // https://tools.ietf.org/html/rfc7231#section-3.1.1

        assertEnhancedHeaders("text/html;charset=UTF-8",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html;charset=\"UTF-8\"",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html;charset=utf-8",
                null, null,
                "text/html", "UTF-8");

        assertEnhancedHeaders("text/html;charset=\"utf-8\"",
                null, null,
                "text/html", "UTF-8");

    }

    private void assertEnhancedHeaders(String httpContentType,
           String inputCollectorContentType, String inputCollectorEncoding,
           String expectedCollectorContentType,
           String expectedCollectorContentEncoding) {

        Properties metadata = new Properties();

        if (httpContentType != null) {
            metadata.add(HTTP_CONTENT_TYPE, httpContentType);
        }
        if (inputCollectorContentType != null) {
            metadata.add(
                    COLLECTOR_CONTENT_TYPE, inputCollectorContentType);
        }
        if (inputCollectorEncoding != null) {
            metadata.add(
                    COLLECTOR_CONTENT_ENCODING, inputCollectorEncoding);
        }
        HttpImporterPipelineUtil.enhanceHTTPHeaders(new HttpDocMetadata(metadata));

        Assertions.assertEquals(expectedCollectorContentType,
                metadata.getString(COLLECTOR_CONTENT_TYPE));
        Assertions.assertEquals(expectedCollectorContentEncoding,
                metadata.getString(COLLECTOR_CONTENT_ENCODING));
    }
}