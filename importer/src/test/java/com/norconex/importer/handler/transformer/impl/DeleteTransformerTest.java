/* Copyright 2010-2024 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;

import org.apache.commons.io.input.NullInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.parser.ParseState;

class DeleteTransformerTest {

    @Test
    void testWriteRead() {
        var t = new DeleteTransformer();
        t.getConfiguration()
                .getFieldMatcher()
                .setPattern("(potato|carrot|document\\.*)")
                .setMethod(Method.REGEX);
        BeanMapper.DEFAULT.assertWriteRead(t);
    }

    @Test
    void testDeleteField() throws IOException {
        var meta = new Properties();
        meta.add("field1", "delete me");
        meta.add("field1", "delete me too");
        meta.set("field2", "delete also");
        meta.set("field3", "keep this one");
        meta.set("field4", "one last to delete");

        var tagger = new DeleteTransformer();
        tagger.getConfiguration()
                .getFieldMatcher()
                .setPattern("(field1|field2|field4\\.*)")
                .setMethod(Method.REGEX);

        InputStream is = new NullInputStream(0);
        tagger.accept(
                TestUtil.newHandlerContext(
                        "blah", is, meta, ParseState.PRE));

        Assertions.assertEquals(1, meta.size(), "Invalid field count");
        Assertions.assertEquals(
                "keep this one", meta.getString("field3"),
                "Value wrongfully deleted or modified");
    }

    @Test
    void testDeleteFieldsViaXMLConfig()
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

        deleteBasic(meta, "X-ACCESS-LEVEL");
        deleteBasic(meta, "X-content-type-options");
        deleteBasic(meta, "X-FRAME-OPTIONS");
        deleteBasic(meta, "X-PARSED-BY");
        deleteBasic(meta, "X-RATE-LIMIT-LIMIT");

        Assertions.assertEquals(3, meta.size(), "Invalid field count");
    }

    private void deleteBasic(Properties meta, String field)
            throws IOException {
        var t = new DeleteTransformer();
        BeanMapper.DEFAULT.read(t, new StringReader("""
                <handler>
                  <fieldMatcher>
                    <ignoreCase>true</ignoreCase>
                    <pattern>%s</pattern>
                  </fieldMatcher>
                </handler>
                """.formatted(field)), Format.XML);
        InputStream is = new NullInputStream(0);
        t.accept(
                TestUtil.newHandlerContext(
                        "blah", is, meta, ParseState.PRE));
    }

    @Test
    void testDeleteFieldsRegexViaXMLConfig()
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

        var t = new DeleteTransformer();
        BeanMapper.DEFAULT.read(t, new StringReader("""
                <handler>
                  <fieldMatcher>
                    <method>regex</method>
                    <pattern>^[Xx]-.*</pattern>
                  </fieldMatcher>
                </handler>
                """), Format.XML);

        InputStream is = new NullInputStream(0);
        t.accept(
                TestUtil.newHandlerContext(
                        "blah", is, meta, ParseState.PRE));

        Assertions.assertEquals(3, meta.size(), "Invalid field count");
    }

    @Test
    void testOnBody() throws IOException {
        var t = new DeleteTransformer();
        var doc = TestUtil.newHandlerContext("blah", "Delete this.");
        t.accept(doc);
        assertThat(doc.input().asInputStream()).isEmpty();
    }
}
