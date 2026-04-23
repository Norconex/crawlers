/* Copyright 2025-2026 Norconex Inc.
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
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.crawler.core.doc.CrawlDocMetaConstants;
import com.norconex.crawler.core.doc.operations.checksum.impl.GenericMetadataChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.impl.GenericMetadataChecksummerConfig;
import com.norconex.crawler.core.doc.operations.checksum.impl.Md5DocumentChecksummer;
import com.norconex.importer.doc.Doc;

@Timeout(30)
class ChecksumTest {

    // --- ChecksumUtil --------------------------------------------------------

    @Test
    void testChecksumMD5_string_returnsNonNull() {
        var cs = ChecksumUtil.checksumMD5("hello world");
        assertThat(cs).isNotBlank().hasSize(32); // MD5 hex = 32 chars
    }

    @Test
    void testChecksumMD5_nullString_returnsNull() {
        assertThat(ChecksumUtil.checksumMD5((String) null)).isNull();
    }

    @Test
    void testChecksumMD5_string_isDeterministic() {
        var cs1 = ChecksumUtil.checksumMD5("same");
        var cs2 = ChecksumUtil.checksumMD5("same");
        assertThat(cs1).isEqualTo(cs2);
    }

    @Test
    void testChecksumMD5_differentStrings_differentChecksums() {
        var cs1 = ChecksumUtil.checksumMD5("foo");
        var cs2 = ChecksumUtil.checksumMD5("bar");
        assertThat(cs1).isNotEqualTo(cs2);
    }

    @Test
    void testChecksumMD5_inputStream_returnsNonNull() throws IOException {
        var bytes = "stream content".getBytes(StandardCharsets.UTF_8);
        var cs = ChecksumUtil.checksumMD5(new ByteArrayInputStream(bytes));
        assertThat(cs).isNotBlank().hasSize(32);
    }

    @Test
    void testMetadataChecksumPlain_returnsFieldValuePairs() {
        var meta = new Properties();
        meta.set("title", "Hello");
        meta.set("author", "World");
        var fm = TextMatcher.regex("title|author");

        var checksum = ChecksumUtil.metadataChecksumPlain(meta, fm);

        assertThat(checksum).isNotBlank();
        assertThat(checksum).contains("title=Hello");
        assertThat(checksum).contains("author=World");
    }

    @Test
    void testMetadataChecksumPlain_nullMetadata_returnsNull() {
        assertThat(ChecksumUtil.metadataChecksumPlain(null,
                TextMatcher.basic("any"))).isNull();
    }

    @Test
    void testMetadataChecksumPlain_nullFieldMatcher_returnsNull() {
        assertThat(ChecksumUtil.metadataChecksumPlain(new Properties(), null))
                .isNull();
    }

    @Test
    void testMetadataChecksumPlain_blankFieldMatcherPattern_returnsNull() {
        var meta = new Properties();
        meta.set("title", "Hello");
        assertThat(ChecksumUtil.metadataChecksumPlain(meta,
                new TextMatcher())).isNull();
    }

    @Test
    void testMetadataChecksumPlain_noMatchingFields_returnsNull() {
        var meta = new Properties();
        meta.set("title", "Hello");
        var checksum = ChecksumUtil.metadataChecksumPlain(meta,
                TextMatcher.basic("nonexistent-field"));
        assertThat(checksum).isNull();
    }

    @Test
    void testMetadataChecksumMD5_returnsHashOfPlainChecksum() {
        var meta = new Properties();
        meta.set("version", "1.0");
        var fm = TextMatcher.basic("version");

        var plain = ChecksumUtil.metadataChecksumPlain(meta, fm);
        var md5 = ChecksumUtil.metadataChecksumMD5(meta, fm);

        assertThat(md5).isEqualTo(ChecksumUtil.checksumMD5(plain));
    }

    // --- GenericMetadataChecksummer (→ AbstractMetadataChecksummer) ----------

    @Test
    void testGenericMetadataChecksummer_basic() {
        var checksummer = new GenericMetadataChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.regex("f1|f2"));

        var meta = new Properties();
        meta.set("f1", "val1");
        meta.set("f2", "val2");

        var checksum = checksummer.createMetadataChecksum(meta);
        assertThat(checksum).isNotBlank();
    }

    @Test
    void testGenericMetadataChecksummer_keep_storesInDefaultField() {
        var checksummer = new GenericMetadataChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("myField"))
                .setKeep(true);

        var meta = new Properties();
        meta.set("myField", "myValue");

        var checksum = checksummer.createMetadataChecksum(meta);
        assertThat(meta.getString(CrawlDocMetaConstants.CHECKSUM_METADATA))
                .isEqualTo(checksum);
    }

    @Test
    void testGenericMetadataChecksummer_keep_storesInCustomField() {
        var checksummer = new GenericMetadataChecksummer();
        checksummer.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("src"))
                .setKeep(true)
                .setToField("my-checksum");

        var meta = new Properties();
        meta.set("src", "valued");

        var checksum = checksummer.createMetadataChecksum(meta);
        assertThat(meta.getString("my-checksum")).isEqualTo(checksum);
    }

    // --- Md5DocumentChecksummer (→ AbstractDocumentChecksummer) ---------------

    @Test
    void testMd5DocumentChecksummer_contentOnly() {
        var checksummer = new Md5DocumentChecksummer();
        var doc = new Doc("ref-1");
        doc.setInputStream(
                new ByteArrayInputStream(
                        "document content".getBytes(StandardCharsets.UTF_8)));

        var checksum = checksummer.createDocumentChecksum(doc);
        assertThat(checksum).isNotBlank().hasSize(32);
    }

    @Test
    void testMd5DocumentChecksummer_keep_storesInMetadata() {
        var checksummer = new Md5DocumentChecksummer();
        checksummer.getConfiguration().setKeep(true);

        var doc = new Doc("ref-keep");
        doc.setInputStream(
                new ByteArrayInputStream(
                        "keep-content".getBytes(StandardCharsets.UTF_8)));

        var checksum = checksummer.createDocumentChecksum(doc);

        // Default field is CHECKSUM_DOC
        assertThat(doc.getMetadata()
                .getString(CrawlDocMetaConstants.CHECKSUM_DOC))
                        .isEqualTo(checksum);
    }

    @Test
    void testMd5DocumentChecksummer_keep_customField() {
        var checksummer = new Md5DocumentChecksummer();
        checksummer.getConfiguration()
                .setKeep(true)
                .setToField("doc-cs");

        var doc = new Doc("ref-field");
        doc.setInputStream(
                new ByteArrayInputStream(
                        "field-content".getBytes(StandardCharsets.UTF_8)));

        var checksum = checksummer.createDocumentChecksum(doc);
        assertThat(doc.getMetadata().getString("doc-cs")).isEqualTo(checksum);
    }

    @Test
    void testGenericMetadataChecksummerConfig_setFieldMatcher() {
        var config = new GenericMetadataChecksummerConfig();
        var fm = TextMatcher.basic("myField");
        config.setFieldMatcher(fm);
        assertThat(config.getFieldMatcher().getPattern()).isEqualTo("myField");
    }
}
