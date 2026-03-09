/* Copyright 2026 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.checksum;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.operations.checksum.impl.Md5DocumentChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.impl.Md5DocumentChecksummerConfig;
import com.norconex.importer.doc.Doc;

@Timeout(30)
class Md5DocumentChecksummerExtraTest {

    @Test
    void fieldsOnlyChecksum_ignoresDocumentContent() {
        var checksummer = new Md5DocumentChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("src"));

        var first = doc("ref-1", "alpha", "src", "same");
        var second = doc("ref-1", "beta", "src", "same");

        assertThat(checksummer.createDocumentChecksum(first))
                .isEqualTo(checksummer.createDocumentChecksum(second));
    }

    @Test
    void combineFieldsAndContent_withoutMatcher_usesAllMetadataAndContent() {
        var checksummer = new Md5DocumentChecksummer();
        checksummer.getConfiguration().setCombineFieldsAndContent(true);

        var first = doc("ref-1", "same-content", "meta", "one");
        var second = doc("ref-1", "same-content", "meta", "two");

        assertThat(checksummer.createDocumentChecksum(first))
                .isNotEqualTo(checksummer.createDocumentChecksum(second));
    }

    @Test
    void configSetFieldMatcher_copiesValuesFromSourceMatcher() {
        var config = new Md5DocumentChecksummerConfig();
        var matcher = TextMatcher.basic("title");

        config.setFieldMatcher(matcher);
        matcher.setPattern("modified-after-copy");

        assertThat(config.getFieldMatcher().getPattern()).isEqualTo("title");
    }

    @Test
    void fieldsOnlyChecksum_returnsNullWhenNoMatchingMetadataExists() {
        var checksummer = new Md5DocumentChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("src"));

        var doc = doc("ref-1", "alpha", "other", "value");

        assertThat(checksummer.createDocumentChecksum(doc)).isNull();
    }

    @Test
    void combineFieldsAndContent_withMatcherUsesBothSources() {
        var checksummer = new Md5DocumentChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("src"))
                .setCombineFieldsAndContent(true);

        var first = doc("ref-1", "same-content", "src", "one");
        var second = doc("ref-1", "same-content", "src", "two");

        assertThat(checksummer.createDocumentChecksum(first))
                .isNotEqualTo(checksummer.createDocumentChecksum(second));
    }

    private Doc doc(String ref, String content, String key, String value) {
        var doc = new Doc(ref);
        doc.setInputStream(new ByteArrayInputStream(
                content.getBytes(StandardCharsets.UTF_8)));
        doc.getMetadata().set(key, value);
        return doc;
    }
}
