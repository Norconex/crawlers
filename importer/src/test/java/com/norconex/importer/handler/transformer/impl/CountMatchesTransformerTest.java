/* Copyright 2016-2023 Norconex Inc.
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

import static java.io.InputStream.nullInputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.io.IOUtils.toInputStream;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class CountMatchesTransformerTest {

    @Test
        void testWriteRead() {
        var t = new CountMatchesTransformer();
        var cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("fromFiel1");
        cfg.setToField("toField1");
        cfg.getCountMatcher().setPattern("value1")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        BeanMapper.DEFAULT.assertWriteRead(t);
    }

    @Test
    void testMatchesCount()
            throws IOException {
        var meta = new Properties();
        meta.add("url", "http://domain/url/test");
        meta.add("fruits", "grapefruit, apple, orange, APPLE");
        var content = "potato carrot Potato";

        CountMatchesTransformer t;
        InputStream is;

        // Count slashes with substrings (4)
        t = new CountMatchesTransformer();
        var cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("url");
        cfg.setToField("slashesCountNormal");
        cfg.getCountMatcher().setPattern("/").setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(4, (int) meta.getInteger("slashesCountNormal"));
        // Count slashes with regex (4)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("url");
        cfg.setToField("slashesCountRegex");
        cfg.getCountMatcher().setPattern("/")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(4, (int) meta.getInteger("slashesCountRegex"));
        // Count URL segments (3)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("url");
        cfg.setToField("segmentCountRegex");
        cfg.getCountMatcher().setPattern("/[^/]+")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(3, (int) meta.getInteger("segmentCountRegex"));

        // Count fruits with substrings case-sensitive (1)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("fruits");
        cfg.setToField("appleCountSensitiveNormal");
        cfg.getCountMatcher().setPattern("apple");
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(1, (int) meta.getInteger("appleCountSensitiveNormal"));
        // Count fruits with substrings case-insensitive (2)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("fruits");
        cfg.setToField("appleCountInsensitiveNormal");
        cfg.getCountMatcher().setPattern("apple").setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(2, (int) meta.getInteger("appleCountInsensitiveNormal"));
        // Count fruits with regex case-sensitive (3)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("fruits");
        cfg.setToField("fruitsCountSensitiveRegex");
        cfg.getCountMatcher().setPattern("(apple|orange|grapefruit)")
                .setMethod(Method.REGEX);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(3, (int) meta.getInteger("fruitsCountSensitiveRegex"));
        // Count fruits with regex case-insensitive (4)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.getFieldMatcher().setPattern("fruits");
        cfg.setToField("fruitsCountInsensitiveRegex");
        cfg.getCountMatcher().setPattern("(apple|orange|grapefruit)")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(4, (int) meta.getInteger("fruitsCountInsensitiveRegex"));

        // Count vegetables with substrings case-sensitive (1)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.setToField("potatoCountSensitiveNormal");
        cfg.getCountMatcher().setPattern("potato");
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(1, (int) meta.getInteger("potatoCountSensitiveNormal"));
        // Count vegetables  with substrings case-insensitive (2)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.setToField("potatoCountInsensitiveNormal");
        cfg.getCountMatcher().setPattern("potato").setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(2, (int) meta.getInteger("potatoCountInsensitiveNormal"));
        // Count vegetables  with regex case-sensitive (2)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.setToField("vegetableCountSensitiveRegex");
        cfg.getCountMatcher().setPattern("(potato|carrot)")
                .setMethod(Method.REGEX);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(2, (int) meta.getInteger("vegetableCountSensitiveRegex"));
        // Count vegetables  with regex case-insensitive (3)
        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.setToField("vegetableCountInsensitiveRegex");
        cfg.getCountMatcher().setPattern("(potato|carrot)")
                .setMethod(Method.REGEX).setIgnoreCase(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(3,
                (int) meta.getInteger("vegetableCountInsensitiveRegex"));
    }

    @Test
    void testLargeContent()
            throws IOException {
        var meta = new Properties();
        meta.add("fruits", "orange orange");
        var content = "potato whatever whatever whatever whatever"
                + "potato whatever whatever whatever whatever";


        CountMatchesTransformer t;
        InputStream is;

        t = new CountMatchesTransformer();
        var cfg = t.getConfiguration();
        cfg.setMaxReadSize(20);
        cfg.setToField("potatoCount");
        cfg.getCountMatcher().setPattern("potato").setPartial(true);

        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(2, (int) meta.getInteger("potatoCount"));

        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.setMaxReadSize(20);
        cfg.getFieldMatcher().setPattern("fruits");
        cfg.setToField("orangeCount");
        cfg.getCountMatcher().setPattern("orange").setPartial(true);
        is = toInputStream(content, UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        assertEquals(2, (int) meta.getInteger("orangeCount"));
    }

    @Test
    void testAddToSameFieldAndNoMatch()
            throws IOException {
        var meta = new Properties();
        meta.add("orange", "orange orange");
        meta.add("apple", "apple apple apple");
        meta.add("potato", "carrot");

        var t = new CountMatchesTransformer();
        var cfg = t.getConfiguration();

        cfg.setMaxReadSize(20);
        cfg.getFieldMatcher().setPattern("(orange|apple)")
                .setMethod(Method.REGEX);
        cfg.setToField("fruitCount");
        cfg.getCountMatcher().setPattern("(orange|apple)")
                .setMethod(Method.REGEX).setPartial(true);
        t.accept(TestUtil.newDocContext(
                "n/a", nullInputStream(), meta, ParseState.POST));
        // we should get the sum of both oranges and apples
        assertEquals(5, (int) meta.getInteger("fruitCount"));

        t = new CountMatchesTransformer();
        cfg = t.getConfiguration();
        cfg.setMaxReadSize(20);
        cfg.getFieldMatcher().setPattern("potato");
        cfg.setToField("potatoCount");
        cfg.getCountMatcher().setPattern("potato").setPartial(true);
        t.accept(TestUtil.newDocContext(
                "n/a", nullInputStream(), meta, ParseState.POST));
        // we should get zero (use string to make sure).
        assertEquals("0", meta.getString("potatoCount"));
    }
}
