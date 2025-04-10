/* Copyright 2022-2024 Norconex Inc.
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetaConstants;
import com.norconex.importer.handler.parser.ParseState;

class DomConditionTest {

    private final String html = """
            <html><head><title>Test page</title></head>\
            <body>This is sample content.<p>\
            <div class="disclaimer">please skip me!</div></body></html>""";

    private final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<food><fruit color=\"red\">an apple</fruit></food>";

    @Test
    void testConditionHTML()
            throws IOException {
        var cond = new DomCondition();
        var metadata = new Properties();
        metadata.set(DocMetaConstants.CONTENT_TYPE, "text/html");

        cond.getConfiguration().setSelector("div.disclaimer");
        Assertions.assertTrue(
                eval(cond, html, metadata),
                "disclaimer should have been accepted.");

        cond.getConfiguration().setSelector("div.disclaimer");
        cond.getConfiguration().setValueMatcher(
                TextMatcher.regex("\\bskip me\\b").partial());
        Assertions.assertTrue(
                eval(cond, html, metadata),
                "disclaimer skip me should have been accepted.");

        cond.getConfiguration().setSelector("div.disclaimer");
        cond.getConfiguration().setValueMatcher(
                TextMatcher.regex("\\bdo not skip me\\b"));
        Assertions.assertFalse(
                eval(cond, html, metadata),
                "disclaimer do not skip me should have been rejected.");
    }

    @Test
    void testConditionXML()
            throws IOException {
        var cond = new DomCondition();
        var metadata = new Properties();
        metadata.set(DocMetaConstants.CONTENT_TYPE, "application/xml");

        cond.getConfiguration().setSelector("food > fruit[color=red]");
        Assertions.assertTrue(
                eval(cond, xml, metadata),
                "Red fruit should have been accepted.");

        cond.getConfiguration().setSelector("food > fruit[color=green]");
        Assertions.assertFalse(
                eval(cond, xml, metadata),
                "Green fruit should have been rejected.");

        cond.getConfiguration().setSelector("food > fruit");
        cond.getConfiguration().setValueMatcher(
                TextMatcher.regex("apple").partial());
        Assertions.assertTrue(
                eval(cond, xml, metadata),
                "Apple should have been accepted.");

        cond.getConfiguration().setSelector("food > fruit");
        cond.getConfiguration().setValueMatcher(TextMatcher.regex("carrot"));
        Assertions.assertFalse(
                eval(cond, xml, metadata),
                "Carrot should have been rejected.");
    }

    @Test
    void testConditionXMLFromField()
            throws IOException {
        var cond = new DomCondition();
        var metadata = new Properties();
        metadata.set(DocMetaConstants.CONTENT_TYPE, "application/xml");
        metadata.set("field1", xml);
        cond.getConfiguration().setFieldMatcher(TextMatcher.basic("field1"));

        cond.getConfiguration().setSelector("food > fruit[color=red]");
        Assertions.assertTrue(
                eval(cond, "n/a", metadata),
                "Red fruit should have been accepted.");

        cond.getConfiguration().setSelector("food > fruit[color=green]");
        Assertions.assertFalse(
                eval(cond, "n/a", metadata),
                "Green fruit should have been rejected.");

        cond.getConfiguration().setSelector("food > fruit");
        cond.getConfiguration().setValueMatcher(
                TextMatcher.regex("apple").partial());
        Assertions.assertTrue(
                eval(cond, "n/a", metadata),
                "Apple should have been accepted.");

        cond.getConfiguration().setSelector("food > fruit");
        cond.getConfiguration().setValueMatcher(TextMatcher.regex("carrot"));
        Assertions.assertFalse(
                eval(cond, "n/a", metadata),
                "Carrot should have been rejected.");
    }

    private boolean eval(
            DomCondition cond,
            String content, Properties metadata)
            throws IOException {
        return TestUtil.condition(
                cond, "n/a", IOUtils.toInputStream(
                        content, StandardCharsets.UTF_8),
                metadata, ParseState.PRE);
    }

    @Test
    void testWriteRead() {
        var cond = new DomCondition();
        cond.getConfiguration().setFieldMatcher(
                TextMatcher.basic("document.contentType"));
        cond.getConfiguration().setValueMatcher(TextMatcher.regex("blah"));
        cond.getConfiguration().setSelector("selector");
        BeanMapper.DEFAULT.assertWriteRead(cond);
    }
}
