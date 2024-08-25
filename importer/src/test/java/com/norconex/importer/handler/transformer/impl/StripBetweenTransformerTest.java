/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.importer.handler.transformer.impl;

import static com.norconex.commons.lang.text.TextMatcher.regex;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.parser.ParseState;

class StripBetweenTransformerTest {

    @Test
    void testTransformTextDocument()
            throws IOException, IOException {
        var t = new StripBetweenTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<h2>").setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("</h2>").setIgnoreCase(true)
                                )
                                .setInclusive(true),
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<P>").setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("</P>").setIgnoreCase(true)
                                )
                                .setInclusive(true),
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<head>").setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("</hEad>").setIgnoreCase(true)
                                )
                                .setInclusive(true),
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<Pre>").setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("</prE>").setIgnoreCase(true)
                                )
                                .setInclusive(true)
                )
        );

        var htmlFile = TestUtil.getAliceHtmlFile();
        try (InputStream is =
                new BufferedInputStream(new FileInputStream(htmlFile))) {
            var metadata = new Properties();
            metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
            var doc = TestUtil.newDocContext(
                    htmlFile.getAbsolutePath(), is, metadata, ParseState.PRE
            );
            t.accept(doc);

            Assertions.assertEquals(
                    443, doc.input().asString().replace("\r", "").length(),
                    "Length of doc content after transformation is incorrect."
            );
        }
    }

    @Test
    void testCollectorHttpIssue237()
            throws IOException, IOException {
        var t = new StripBetweenTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<body>").setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("<\\!-- START -->")
                                                .setIgnoreCase(true)
                                )
                                .setInclusive(true),
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<\\!-- END -->")
                                                .setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("<\\!-- START -->")
                                                .setIgnoreCase(true)
                                )
                                .setInclusive(true),
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<\\!-- END -->")
                                                .setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("</body>").setIgnoreCase(true)
                                )
                                .setInclusive(true)
                )
        );

        var html = """
                <html>
                  <body>
                    ignore this text
                    <!-- START -->extract me 1<!-- END -->
                    ignore this text
                    <!-- START -->extract me 2<!-- END -->
                    ignore this text
                  </body>
                </html>""";

        try (var is = new ByteArrayInputStream(html.getBytes())) {
            var metadata = new Properties();
            metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
            var doc = TestUtil.newDocContext(
                    "fake.html",
                    is, metadata, ParseState.PRE
            );
            t.accept(doc);
            assertThat(doc.input().asString().replace("\r", ""))
                    .isEqualToIgnoringWhitespace(
                            "<html>extract me 1extract me 2</html>"
                    );
        }
    }

    @Test
    void testWriteRead() {
        var t = new StripBetweenTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<!-- NO INDEX")
                                                .setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("/NOINDEX -->")
                                                .setIgnoreCase(true)
                                )
                                .setInclusive(true),
                        new StripBetweenOperation()
                                .setStartMatcher(
                                        regex("<!-- HEADER START")
                                                .setIgnoreCase(true)
                                )
                                .setEndMatcher(
                                        regex("HEADER END -->")
                                                .setIgnoreCase(true)
                                )
                                .setInclusive(true)
                )
        );
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }
}
