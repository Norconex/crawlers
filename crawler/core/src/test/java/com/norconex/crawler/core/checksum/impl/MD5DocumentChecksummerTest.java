/* Copyright 2015-2022 Norconex Inc.
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
package com.norconex.crawler.core.checksum.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;

class MD5DocumentChecksummerTest {

    @Test
    void testCreateDocumentChecksumFromContent() throws IOException {
        // Simply should not fail and return something.
        var content = "Some content";
        var is = new CachedStreamFactory(1024, 1024).newInputStream(content);
        var doc = new Doc("N/A", is);
        var cs = new MD5DocumentChecksummer();
        var checksum = cs.createDocumentChecksum(doc);
        is.dispose();
        assertThat(checksum).isEqualTo("b53227da4280f0e18270f21dd77c91d0");
    }

    @Test
    void testCreateChecksumFromCombinedContentAndFields() throws IOException {
        // Simply should not fail and return something.
        var content = "Some content";
        var is = new CachedStreamFactory(1024, 1024).newInputStream(content);
        var props = new Properties();
        props.add("field1", "value1");
        props.add("field2", "value2");
        var doc = new Doc("N/A", is, props);
        var cs = new MD5DocumentChecksummer();
        cs.getConfiguration().setCombineFieldsAndContent(true);
        var checksum = cs.createDocumentChecksum(doc);
        is.dispose();
        assertThat(checksum).isEqualTo("e75e091aff05a39a9c585e2b4b18c9bc"
                + "|b53227da4280f0e18270f21dd77c91d0");
    }

    @Test
    void testCreateDocumentChecksumFromMeta() throws IOException {
        // Simply should not fail and return something.
        var is =
                new CachedStreamFactory(1024, 1024).newInputStream();
        var doc = new Doc("N/A", is);
        doc.getMetadata().add("field1", "value1.1", "value1.2");
        doc.getMetadata().add("field2", "value2");
        var cs = new MD5DocumentChecksummer();

        // 2 matching fields
        cs.getConfiguration().setFieldMatcher(TextMatcher.csv("field1,field2"));
        var checksum1 = cs.createDocumentChecksum(doc);
        Assertions.assertTrue(
                StringUtils.isNotBlank(checksum1),
                "No checksum was generated for two matching fields.");

        // 1 out of 2 matching fields
        cs.getConfiguration().setFieldMatcher(TextMatcher.csv("field1,field3"));
        var checksum2 = cs.createDocumentChecksum(doc);
        Assertions.assertTrue(
                StringUtils.isNotBlank(checksum2),
                "No checksum was generated for 1 of two matching fields.");

        // No matching fields
        cs.getConfiguration().setFieldMatcher(TextMatcher.csv("field4,field5"));
        var checksum3 = cs.createDocumentChecksum(doc);
        Assertions.assertNull(checksum3,
                "Checksum for no matching fields should have been null.");

        // Regex
        cs.getConfiguration().setFieldMatcher(TextMatcher.regex("field.*"));
        var checksum4 = cs.createDocumentChecksum(doc);
        Assertions.assertTrue(StringUtils.isNotBlank(checksum4),
                "No checksum was generated.");


        // Regex only no match
        cs.getConfiguration().setFieldMatcher(TextMatcher.regex("NOfield.*"));
        var checksum5 = cs.createDocumentChecksum(doc);
        Assertions.assertNull(checksum5,
                "Checksum for no matching regex should have been null.");

        is.dispose();
    }

    // https://github.com/Norconex/collector-http/issues/388
    @Test
    void testCombineFieldsAndContent() throws IOException {
        // Simply should not fail and return something.
        var is =
                new CachedStreamFactory(1024, 1024).newInputStream("Content");
        var doc = new Doc("N/A", is);
        doc.getMetadata().add("field1", "value1.1", "value1.2");
        doc.getMetadata().add("field2", "value2");
        var cs = new MD5DocumentChecksummer();

        // With no source fields, should use content only.
        var contentChecksum = cs.createDocumentChecksum(doc);

        // With source fields, should use fields only.
        cs.getConfiguration().setFieldMatcher(TextMatcher.regex("field.*"));
        var fieldsChecksum = cs.createDocumentChecksum(doc);

        // When combining, should use both fields and content.
        cs.getConfiguration().setCombineFieldsAndContent(true);
        var combinedChecksum = cs.createDocumentChecksum(doc);

        // The 3 checksums should be non-null, but different.
        Assertions.assertNotNull( contentChecksum,
                "Null content checksum.");
        Assertions.assertNotNull( fieldsChecksum,
                "Null fields checksum.");
        Assertions.assertNotNull( combinedChecksum,
                "Null combined checksum.");

        Assertions.assertNotEquals(contentChecksum, fieldsChecksum);
        Assertions.assertNotEquals(fieldsChecksum, combinedChecksum);

        is.dispose();
    }

    @Test
    void testWriteRead() {
        var c = new MD5DocumentChecksummer();
        c.getConfiguration()
            .setFieldMatcher(TextMatcher.csv("field1,field2"))
            .setCombineFieldsAndContent(true)
            .setKeep(true)
            .setToField("myToField")
            .setOnSet(PropertySetter.PREPEND)
            ;
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(c, "documentChecksummer"));
    }
}
