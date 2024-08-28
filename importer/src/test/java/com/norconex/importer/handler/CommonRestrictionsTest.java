/* Copyright 2022-2024 Norconex Inc.
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
package com.norconex.importer.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatchers;
import com.norconex.importer.doc.DocMetadata;

class CommonRestrictionsTest {

    @Test
    void testXmlFeedContentTypes() {
        assertThat(
                test(
                        CommonRestrictions::xmlFeedContentTypes,
                        "application/atom+xml",
                        "application/rss+xml",
                        "application/rdf+xml",
                        "application/xml",
                        "text/xml"
                )
        ).isTrue();

        assertThat(
                test(
                        CommonRestrictions::xmlFeedContentTypes,
                        "text/blah"
                )
        ).isFalse();
    }

    @Test
    void testDomContentTypes() {
        assertThat(
                test(
                        CommonRestrictions::domContentTypes,
                        "application/atom+xml",
                        "application/mathml+xml",
                        "application/rss+xml",
                        "application/vnd.wap.xhtml+xml",
                        "application/x-asp",
                        "application/xhtml+xml",
                        "application/xml",
                        "application/xslt+xml",
                        "image/svg+xml",
                        "text/html",
                        "text/xml"
                )
        ).isTrue();

        assertThat(
                test(
                        CommonRestrictions::domContentTypes,
                        "text/blah"
                )
        ).isFalse();
    }

    @Test
    void testHtmlContentTypes() {
        assertThat(
                test(
                        CommonRestrictions::htmlContentTypes,
                        "application/vnd.wap.xhtml+xml",
                        "application/xhtml+xml",
                        "text/html"
                )
        ).isTrue();

        assertThat(
                test(
                        CommonRestrictions::htmlContentTypes,
                        "text/blah"
                )
        ).isFalse();
    }

    @Test
    void testXmlContentTypes() {
        assertThat(
                test(
                        CommonRestrictions::xmlContentTypes,
                        "application/atom+xml",
                        "application/mathml+xml",
                        "application/rss+xml",
                        "application/xhtml+xml",
                        "application/xml",
                        "application/xslt+xml",
                        "image/svg+xml",
                        "text/xml"
                )
        ).isTrue();

        assertThat(
                test(
                        CommonRestrictions::xmlContentTypes,
                        "text/blah"
                )
        ).isFalse();
    }

    @Test
    void testImageIOStandardContentTypes() {
        assertThat(
                test(
                        CommonRestrictions::imageIOStandardContentTypes,
                        "image/bmp",
                        "image/gif",
                        "image/jpeg",
                        "image/png",
                        "image/vnd.wap.wbmp",
                        "image/x-windows-bmp"
                )
        ).isTrue();

        assertThat(
                test(
                        CommonRestrictions::imageIOStandardContentTypes,
                        "text/blah"
                )
        ).isFalse();
    }

    private boolean test(
            Function<String, PropertyMatchers> restrictions,
            String... contentTypes
    ) {
        var props = new Properties();
        for (String contentType : contentTypes) {
            props.set(DocMetadata.CONTENT_TYPE, contentType);
            if (!restrictions.apply(DocMetadata.CONTENT_TYPE).test(props)) {
                return false;
            }
        }
        return true;
    }
}
