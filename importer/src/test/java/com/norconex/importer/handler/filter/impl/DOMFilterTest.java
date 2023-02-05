/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.importer.handler.filter.impl;

import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;

class DOMFilterTest {

    private final String html = """
    	<html><head><title>Test page</title></head>\
    	<body>This is sample content.<p>\
    	<div class="disclaimer">please skip me!</div></body></html>""";

    private final String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
            + "<food><fruit color=\"red\">an apple</fruit></food>";

    @Test
    void testFilterHTML()
            throws ImporterHandlerException {
        var filter = new DOMFilter();
        var metadata = new Properties();
        metadata.set(
                DocMetadata.CONTENT_TYPE, "text/html");
        filter.setOnMatch(OnMatch.EXCLUDE);

        filter.setSelector("div.disclaimer");
        Assertions.assertFalse(filter(filter, html, metadata),
                "disclaimer should have been rejected.");

        filter.setSelector("div.disclaimer");
        filter.setValueMatcher(TextMatcher.regex("\\bskip me\\b").partial());
        Assertions.assertFalse(filter(filter, html, metadata),
                "disclaimer skip me should have been rejected.");

        filter.setSelector("div.disclaimer");
        filter.setValueMatcher(TextMatcher.regex("\\bdo not skip me\\b"));
        Assertions.assertTrue(filter(filter, html, metadata),
                "disclaimer do not skip me should have been accepted.");
    }


    @Test
    void testFilterXML()
            throws ImporterHandlerException {
        var filter = new DOMFilter();
        var metadata = new Properties();
        metadata.set(
                DocMetadata.CONTENT_TYPE, "application/xml");
        filter.setOnMatch(OnMatch.INCLUDE);

        filter.setSelector("food > fruit[color=red]");
        Assertions.assertTrue(filter(filter, xml, metadata),
                "Red fruit should have been accepted.");

        filter.setSelector("food > fruit[color=green]");
        Assertions.assertFalse(filter(filter, xml, metadata),
                "Green fruit should have been rejected.");

        filter.setSelector("food > fruit");
        filter.setValueMatcher(TextMatcher.regex("apple").partial());
        Assertions.assertTrue(filter(filter, xml, metadata),
                "Apple should have been accepted.");

        filter.setSelector("food > fruit");
        filter.setValueMatcher(TextMatcher.regex("carrot"));
        Assertions.assertFalse(filter(filter, xml, metadata),
                "Carrot should have been rejected.");
    }

    @Test
    void testFilterXMLFromField()
            throws ImporterHandlerException {
        var filter = new DOMFilter();
        var metadata = new Properties();
        metadata.set(
                DocMetadata.CONTENT_TYPE, "application/xml");
        metadata.set("field1", xml);
        filter.setOnMatch(OnMatch.INCLUDE);
        filter.setFieldMatcher(TextMatcher.basic("field1"));

        filter.setSelector("food > fruit[color=red]");
        Assertions.assertTrue(filter(filter, "n/a", metadata),
                "Red fruit should have been accepted.");

        filter.setSelector("food > fruit[color=green]");
        Assertions.assertFalse(filter(filter, "n/a", metadata),
                "Green fruit should have been rejected.");

        filter.setSelector("food > fruit");
        filter.setValueMatcher(TextMatcher.regex("apple").partial());
        Assertions.assertTrue(filter(filter, "n/a", metadata),
                "Apple should have been accepted.");

        filter.setSelector("food > fruit");
        filter.setValueMatcher(TextMatcher.regex("carrot"));
        Assertions.assertFalse(filter(filter, "n/a", metadata),
                "Carrot should have been rejected.");
    }

    private boolean filter(DOMFilter filter,
            String content, Properties metadata)
                    throws ImporterHandlerException {
        return TestUtil.filter(filter, "n/a", IOUtils.toInputStream(
                content, StandardCharsets.UTF_8), metadata, ParseState.PRE);
    }

    @Test
        void testWriteRead() {
        var filter = new DOMFilter();
        filter.addRestriction(new PropertyMatcher(
                TextMatcher.basic("document.contentType"),
                TextMatcher.basic("text/html")));
        filter.setValueMatcher(TextMatcher.regex("blah"));
        filter.setOnMatch(OnMatch.INCLUDE);
        filter.setSelector("selector");
        XML.assertWriteRead(filter, "handler");
    }
}
