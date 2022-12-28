/* Copyright 2017-2022 Norconex Inc.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class TruncateTaggerTest {

    @Test
    void testWriteRead() {
        var t = new TruncateTagger();
        t.setAppendHash(true);
        t.setFieldMatcher(TextMatcher.basic("fromField"));
        t.setMaxLength(100);
        t.setOnSet(PropertySetter.REPLACE);
        t.setSuffix("suffix");
        t.setToField("toField");
        t.addRestriction(new PropertyMatcher(
                TextMatcher.regex("field"),
                TextMatcher.regex("value")));

        XML.assertWriteRead(t, "handler");
    }

    @Test
    void testWithSuffixAndHash() throws ImporterHandlerException {
        var metadata = new Properties();
        metadata.add("from",
                "Please truncate me before you start thinking I am too long.",
                "Another long string to test similar with suffix and no hash",
                "Another long string to test similar without suffix, a hash",
                "Another long string to test similar without suffix, no hash",
                "A small one");

        var t = new TruncateTagger();

        t.setFieldMatcher(TextMatcher.basic("from"));
        t.setToField("to");
        t.setMaxLength(50);
        t.setOnSet(PropertySetter.REPLACE);

        // hash + suffix
        t.setAppendHash(true);
        t.setSuffix("!");
        TestUtil.tag(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Please truncate me before you start thi!0996700004",
                metadata.getStrings("to").get(0));
        Assertions.assertNotEquals("Must have different hashes",
                metadata.getStrings("to").get(1),
                metadata.getStrings("to").get(2));

        // no hash + suffix
        t.setAppendHash(false);
        t.setSuffix("...");
        TestUtil.tag(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Another long string to test similar with suffix...",
                metadata.getStrings("to").get(1));

        // no hash + suffix
        t.setAppendHash(true);
        t.setSuffix(null);
        TestUtil.tag(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Another long string to test similar with0939281732",
                metadata.getStrings("to").get(2));

        // no hash + no suffix
        t.setAppendHash(false);
        t.setSuffix(null);
        TestUtil.tag(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "Another long string to test similar without suffix",
                metadata.getStrings("to").get(3));

        // too small for truncate
        t.setAppendHash(false);
        t.setSuffix(null);
        TestUtil.tag(t, "n/a", metadata, ParseState.PRE);
        Assertions.assertEquals(
                "A small one", metadata.getStrings("to").get(4));
    }
}
