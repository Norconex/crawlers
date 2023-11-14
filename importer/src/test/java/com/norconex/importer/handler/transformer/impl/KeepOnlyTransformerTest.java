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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.StringReader;

import org.apache.commons.io.input.NullInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class KeepOnlyTransformerTest {

    @Test
    void testWriteRead() {
        var t = new KeepOnlyTransformer();
        t.getConfiguration()
            .getFieldMatcher().setPattern("field1|field2|field3");
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(t));
    }

    @Test
    void testKeepAllFields() throws Exception {

        var metadata = new Properties();
        metadata.add("key1", "value1");
        metadata.add("key2", "value2");
        metadata.add("key3", "value3");

        // Should keep all keys
        var t = new KeepOnlyTransformer();
        t.getConfiguration().getFieldMatcher()
                .setPattern("(key1|key2|key3)").setMethod(Method.REGEX);
        TestUtil.transform(t, "reference", metadata, ParseState.POST);

        assertEquals(3, metadata.size());
    }

    @Test
    void testKeepSingleField() throws Exception {

        var metadata = new Properties();
        metadata.add("key1", "value1");
        metadata.add("key2", "value2");
        metadata.add("key3", "value3");

        // Should only keep key1
        var t = new KeepOnlyTransformer();
        t.getConfiguration().setFieldMatcher(TextMatcher.basic("key1"));
        TestUtil.transform(t, "reference", metadata, ParseState.POST);

        assertEquals(1, metadata.size());
        assertTrue(metadata.containsKey("key1"));
    }

    @Test
    void testDeleteAllMetadata() throws Exception {

        var metadata = new Properties();
        metadata.add("key1", "value1");
        metadata.add("key2", "value2");
        metadata.add("key3", "value3");

        // Because we are not adding any field to keep, all metadata should be
        // deleted
        var t = new KeepOnlyTransformer();
        t.getConfiguration().getFieldMatcher().setPattern("IAmNotThere");
        TestUtil.transform(t, "reference", metadata, ParseState.POST);

        assertTrue(metadata.isEmpty());
    }

    @Test
    void testKeepFieldsRegexViaXMLConfig()
            throws IOException {
        var meta = new Properties();
        meta.add("content-type", "blah");
        meta.add("x-access-level", "blah");
        meta.add("X-CONTENT-TYPE-OPTIONS", "blah");
        meta.add("X-FRAME-OPTIONS", "blah");
        meta.add("X-PARSED-BY", "blah");
        meta.add("date", "blah");
        meta.add("X-RATE-LIMIT-LIMIT", "blah");
        meta.add("source", "blah");

        var t = new KeepOnlyTransformer();
        BeanMapper.DEFAULT.read(t, new StringReader("""
                <test>
                  <fieldMatcher>
                    <method>regex</method>
                    <pattern>[Xx]-.*</pattern>
                  </fieldMatcher>
                </test>"""),
                Format.XML);

        InputStream is = new NullInputStream(0);
        TestUtil.transform(t, "blah", is, meta, ParseState.PRE);

        Assertions.assertEquals(5, meta.size(), "Invalid field count");
    }
}
