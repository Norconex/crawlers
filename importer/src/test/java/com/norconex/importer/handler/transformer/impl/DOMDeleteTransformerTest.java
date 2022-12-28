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
package com.norconex.importer.handler.transformer.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

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
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.DOMUtil;

class DOMDeleteTransformerTest {

    @Test
    void testDelete() throws ImporterHandlerException, IOException {

        var child1 = """
        	<div id="childOneId" class="childClass">\
        	<a href="http://example.org/doc.html">\
        	Child1 Link</a></div>""";
        var child2 = "<div class=\"childClass\">Child2 text</div>";

        var full = "<div id=\"parentId\" class=\"parentClass\">"
                + child1 + child2 + "</div>";

        var fullMinusChild1 = "<div id=\"parentId\" class=\"parentClass\">"
                + child2 + "</div>";


        var t = new DOMDeleteTransformer();
        t.setParser(DOMUtil.PARSER_XML);
        t.setSourceCharset("UTF-8");

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");

        t.addSelector("#childOneId");

        var content = IOUtils.toInputStream(full, UTF_8);
        var os = new ByteArrayOutputStream();
        t.transformDocument(TestUtil.newHandlerDoc("n/a", content, metadata),
                content, os, ParseState.PRE);

        var output = os.toString(UTF_8.toString());
        content.close();
        os.close();
        Assertions.assertEquals(fullMinusChild1, cleanHTML(output));
    }

    @Test
    void testNestedDelete()
            throws ImporterHandlerException, IOException {

        var child1 = """
        	<div id="childOneId" class="childClass">\
        	<a href="http://example.org/doc.html">\
        	Child1 Link</a></div>""";
        var child2 = "<div class=\"childClass\">Child2 text</div>";

        var full = "<div id=\"parentId\" class=\"parentClass\">"
                + child1 + child2 + "</div>";

        var t = new DOMDeleteTransformer();
        t.setParser(DOMUtil.PARSER_XML);
        t.setSourceCharset("UTF-8");

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.addSelector("div");

        var content = IOUtils.toInputStream(full, UTF_8);
        var os = new ByteArrayOutputStream();
        t.transformDocument(TestUtil.newHandlerDoc("n/a", content, metadata),
                content, os, ParseState.PRE);

        var output = os.toString(UTF_8.toString());
        content.close();
        os.close();
        Assertions.assertEquals("", cleanHTML(output));
    }
    private String cleanHTML(String html) {
        var clean = html;
        clean = clean.replaceAll("[\\r\\n]", "");
        clean = clean.replaceAll(">\\s+", ">");
        return clean.replaceAll("\\s+<", "<");
    }

    @Test
    void testWriteRead() {
        var tagger = new DOMDeleteTransformer();
        tagger.addSelector("p.blah > a");
        tagger.setParser("xml");
        tagger.setSourceCharset("UTF-8");
        tagger.addRestriction(new PropertyMatcher(
                TextMatcher.basic("afield"),
                TextMatcher.basic("aregex")));
        XML.assertWriteRead(tagger, "handler");
    }
}
