/* Copyright 2017-2024 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.DocHandlerContext;
import com.norconex.importer.handler.parser.ParseState;

class TruncateTransformerTest {

    @Test
    void testWriteRead() {
        var t = new TruncateTransformer();
        t.getConfiguration()
                .setAppendHash(true)
                .setFieldMatcher(TextMatcher.basic("fromField"))
                .setMaxLength(100)
                .setOnSet(PropertySetter.REPLACE)
                .setSuffix("suffix")
                .setToField("toField");

        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }

    @Test
    void testFieldWithSuffixAndHash() throws IOException {
        var metadata = new Properties();
        metadata.add(
                "from",
                "Please truncate me before you start thinking I am too long.",
                "Another long string to test similar with suffix and no hash",
                "Another long string to test similar without suffix, a hash",
                "Another long string to test similar without suffix, no hash",
                "A small one");

        var t = new TruncateTransformer();
        t.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("from"))
                .setToField("to")
                .setMaxLength(50)
                .setOnSet(PropertySetter.REPLACE)
                // hash + suffix
                .setAppendHash(true)
                .setSuffix("!");
        TestUtil.transform(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Please truncate me before you start thi!0996700004",
                metadata.getStrings("to").get(0));
        Assertions.assertNotEquals(
                "Must have different hashes",
                metadata.getStrings("to").get(1),
                metadata.getStrings("to").get(2));

        // no hash + suffix
        t.getConfiguration()
                .setAppendHash(false)
                .setSuffix("...");
        TestUtil.transform(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Another long string to test similar with suffix...",
                metadata.getStrings("to").get(1));

        // no hash + suffix
        t.getConfiguration()
                .setAppendHash(true)
                .setSuffix(null);
        TestUtil.transform(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Another long string to test similar with0939281732",
                metadata.getStrings("to").get(2));

        // no hash + no suffix
        t.getConfiguration()
                .setAppendHash(false)
                .setSuffix(null);
        TestUtil.transform(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Another long string to test similar without suffix",
                metadata.getStrings("to").get(3));

        // too small for truncate
        t.getConfiguration()
                .setAppendHash(false)
                .setSuffix(null);
        TestUtil.transform(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "A small one", metadata.getStrings("to").get(4));
    }

    @Test
    void testBodyWithSuffixAndHash() throws IOException {
        DocHandlerContext docCtx;
        var t = new TruncateTransformer();

        // hash + suffix
        docCtx = TestUtil.newHandlerContext(
                "Please truncate me before you start thinking I am too long.");
        t.getConfiguration()
                .setToField("to")
                .setMaxLength(50)
                .setOnSet(PropertySetter.REPLACE)
                .setAppendHash(true)
                .setSuffix("!");
        t.handle(docCtx);
        Assertions.assertEquals(
                "Please truncate me before you start thi!0996700004",
                docCtx.metadata().getString("to"));

        // no hash + suffix
        docCtx = TestUtil.newHandlerContext(
                "Another long string to test similar with suffix and no hash");
        t.getConfiguration()
                .setAppendHash(false)
                .setSuffix("...");
        t.handle(docCtx);
        Assertions.assertEquals(
                "Another long string to test similar with suffix...",
                docCtx.metadata().getString("to"));

        // hash + no suffix
        docCtx = TestUtil.newHandlerContext(
                "Another long string to test similar without suffix, a hash");
        t.getConfiguration()
                .setAppendHash(true)
                .setSuffix(null);
        t.handle(docCtx);
        Assertions.assertEquals(
                "Another long string to test similar with0939281732",
                docCtx.metadata().getString("to"));

        // no hash + no suffix
        docCtx = TestUtil.newHandlerContext(
                "Another long string to test similar without suffix, no hash");
        t.getConfiguration()
                .setAppendHash(false)
                .setSuffix(null);
        t.handle(docCtx);
        Assertions.assertEquals(
                "Another long string to test similar without suffix",
                docCtx.metadata().getString("to"));

        // too small for truncate
        docCtx = TestUtil.newHandlerContext("A small one");
        t.getConfiguration()
                .setAppendHash(false)
                .setSuffix(null);
        t.handle(docCtx);
        Assertions.assertEquals(
                "A small one", docCtx.metadata().getString("to"));

        // hash + suffix, replacing body
        docCtx = TestUtil.newHandlerContext(
                "Please truncate me before you start thinking I am too long.");
        t = new TruncateTransformer();
        t.getConfiguration()
                .setToField(null)
                .setMaxLength(50)
                .setOnSet(PropertySetter.REPLACE)
                .setAppendHash(true)
                .setSuffix("!");
        t.handle(docCtx);
        Assertions.assertEquals(
                "Please truncate me before you start thi!0996700004",
                docCtx.input().asString());

        // doing fields, without "to" field
        docCtx = TestUtil.newHandlerContext("Content");
        docCtx.metadata().add("key", "I should be truncated.");
        t = new TruncateTransformer();
        t.getConfiguration()
                .setToField(null)
                .setMaxLength(10)
                .setOnSet(PropertySetter.REPLACE)
                .setFieldMatcher(TextMatcher.basic("key"));
        t.doFields(docCtx);
        Assertions.assertEquals("Content", docCtx.input().asString());
        assertThat(docCtx.metadata().getString("key")).isEqualTo("I should b");
    }
}
