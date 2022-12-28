/* Copyright 2016-2022 Norconex Inc.
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
package com.norconex.importer.handler.tagger.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class CountMatchesTaggerTest {

    @Test
        void testWriteRead() {
        var t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("fromFiel1");
        t.setToField("toField1");
        t.getCountMatcher().setPattern("value1")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        XML.assertWriteRead(t, "handler");
    }

    @Test
    void testMatchesCount()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("url", "http://domain/url/test");
        meta.add("fruits", "grapefruit, apple, orange, APPLE");
        var content = "potato carrot Potato";

        CountMatchesTagger t;
        InputStream is;

        // Count slashes with substrings (4)
        t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("url");
        t.setToField("slashesCountNormal");
        t.getCountMatcher().setPattern("/").setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(4, (int) meta.getInteger("slashesCountNormal"));
        // Count slashes with regex (4)
        t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("url");
        t.setToField("slashesCountRegex");
        t.getCountMatcher().setPattern("/")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(4, (int) meta.getInteger("slashesCountRegex"));
        // Count URL segments (3)
        t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("url");
        t.setToField("segmentCountRegex");
        t.getCountMatcher().setPattern("/[^/]+")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(3, (int) meta.getInteger("segmentCountRegex"));

        // Count fruits with substrings case-sensitive (1)
        t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("fruits");
        t.setToField("appleCountSensitiveNormal");
        t.getCountMatcher().setPattern("apple");
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(1, (int) meta.getInteger("appleCountSensitiveNormal"));
        // Count fruits with substrings case-insensitive (2)
        t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("fruits");
        t.setToField("appleCountInsensitiveNormal");
        t.getCountMatcher().setPattern("apple").setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(2, (int) meta.getInteger("appleCountInsensitiveNormal"));
        // Count fruits with regex case-sensitive (3)
        t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("fruits");
        t.setToField("fruitsCountSensitiveRegex");
        t.getCountMatcher().setPattern("(apple|orange|grapefruit)")
                .setMethod(Method.REGEX);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(3, (int) meta.getInteger("fruitsCountSensitiveRegex"));
        // Count fruits with regex case-insensitive (4)
        t = new CountMatchesTagger();
        t.getFieldMatcher().setPattern("fruits");
        t.setToField("fruitsCountInsensitiveRegex");
        t.getCountMatcher().setPattern("(apple|orange|grapefruit)")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(4, (int) meta.getInteger("fruitsCountInsensitiveRegex"));

        // Count vegetables with substrings case-sensitive (1)
        t = new CountMatchesTagger();
        t.setToField("potatoCountSensitiveNormal");
        t.getCountMatcher().setPattern("potato");
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(1, (int) meta.getInteger("potatoCountSensitiveNormal"));
        // Count vegetables  with substrings case-insensitive (2)
        t = new CountMatchesTagger();
        t.setToField("potatoCountInsensitiveNormal");
        t.getCountMatcher().setPattern("potato").setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(2, (int) meta.getInteger("potatoCountInsensitiveNormal"));
        // Count vegetables  with regex case-sensitive (2)
        t = new CountMatchesTagger();
        t.setToField("vegetableCountSensitiveRegex");
        t.getCountMatcher().setPattern("(potato|carrot)")
                .setMethod(Method.REGEX);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(2, (int) meta.getInteger("vegetableCountSensitiveRegex"));
        // Count vegetables  with regex case-insensitive (3)
        t = new CountMatchesTagger();
        t.setToField("vegetableCountInsensitiveRegex");
        t.getCountMatcher().setPattern("(potato|carrot)")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(3,
                (int) meta.getInteger("vegetableCountInsensitiveRegex"));
    }

    @Test
    void testLargeContent()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("fruits", "orange orange");
        var content = "potato whatever whatever whatever whatever"
                + "potato whatever whatever whatever whatever";


        CountMatchesTagger t;
        InputStream is;

        t = new CountMatchesTagger();
        t.setMaxReadSize(20);
        t.setToField("potatoCount");
        t.getCountMatcher().setPattern("potato").setPartial(true);

        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(2, (int) meta.getInteger("potatoCount"));

        t = new CountMatchesTagger();
        t.setMaxReadSize(20);
        t.getFieldMatcher().setPattern("fruits");
        t.setToField("orangeCount");
        t.getCountMatcher().setPattern("orange").setPartial(true);
        is = toInputStream(content, UTF_8);
        t.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        assertEquals(2, (int) meta.getInteger("orangeCount"));
    }

    @Test
    void testAddToSameFieldAndNoMatch()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("orange", "orange orange");
        meta.add("apple", "apple apple apple");
        meta.add("potato", "carrot");

        CountMatchesTagger t = new CountMatchesTagger();

        t.setMaxReadSize(20);
        t.getFieldMatcher().setPattern("(orange|apple)")
                .setMethod(Method.REGEX);
        t.setToField("fruitCount");
        t.getCountMatcher().setPattern("(orange|apple)")
                .setMethod(Method.REGEX).setPartial(true);
        TestUtil.tag(t, "n/a", meta, ParseState.POST);

        // we should get the sum of both oranges and apples
        assertEquals(5, (int) meta.getInteger("fruitCount"));

        t = new CountMatchesTagger();
        t.setMaxReadSize(20);
        t.getFieldMatcher().setPattern("potato");
        t.setToField("potatoCount");
        t.getCountMatcher().setPattern("potato").setPartial(true);
        TestUtil.tag(t, "n/a", meta, ParseState.POST);
        // we should get zero (use string to make sure).
        assertEquals("0", meta.getString("potatoCount"));
    }
}
