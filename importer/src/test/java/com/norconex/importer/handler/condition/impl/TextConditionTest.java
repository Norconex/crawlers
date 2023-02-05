/* Copyright 2022 Norconex Inc.
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
package com.norconex.importer.handler.condition.impl;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class TextConditionTest {

    @Test
    void testRegexContentMatches()
            throws ImporterHandlerException {
        var cond = newRegexTextCondition();
        cond.getValueMatcher().setPattern(".*string.*");
        Assertions.assertTrue(TestUtil.condition(cond, "n/a",
                IOUtils.toInputStream("a string that matches",
                        StandardCharsets.UTF_8), null, ParseState.PRE),
                "Should have been accepted.");
    }

    @Test
    void testRegexContentNoMatches()
            throws ImporterHandlerException {
        var cond = newRegexTextCondition();
        cond.getValueMatcher().setPattern(".*string.*");
        Assertions.assertFalse(
                TestUtil.condition(cond, "n/a", IOUtils.toInputStream(
                        "a text that does not match", StandardCharsets.UTF_8),
                        null, ParseState.PRE),
                "Should have been rejected.");
    }


    @Test
    void testRegexFieldDocument()
            throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("field1", "a string to match");
        meta.add("field2", "something we want");

        var cond = newRegexTextCondition();

        cond.getFieldMatcher().setPattern("field1");
        cond.getValueMatcher().setPattern(".*string.*");

        Assertions.assertTrue(
                TestUtil.condition(cond, "n/a", null, meta, ParseState.PRE),
                "field1 not conditioned properly.");

        cond.getFieldMatcher().setPattern("field2");
        Assertions.assertFalse(
                TestUtil.condition(cond, "n/a", null, meta, ParseState.PRE),
                "field2 not conditioned properly.");
    }

    @Test
        void testWriteRead() {
        var cond = new TextCondition();
        cond.setFieldMatcher(new TextMatcher()
                .setMethod(Method.REGEX)
                .setPartial(true));
        cond.setValueMatcher(new TextMatcher()
                .setMethod(Method.REGEX)
                .setPartial(true)
                .setPattern("blah"));
        XML.assertWriteRead(cond, "condition");
    }

    private TextCondition newRegexTextCondition() {
        return new TextCondition(newRegexMatcher(), newRegexMatcher());
    }
    private TextMatcher newRegexMatcher() {
        return new TextMatcher().setMethod(Method.REGEX);
    }
}
