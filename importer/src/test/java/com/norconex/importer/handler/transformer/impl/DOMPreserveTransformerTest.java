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
package com.norconex.importer.handler.transformer.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.impl.DOMPreserveTransformer.DOMExtractDetails;
import com.norconex.importer.parser.ParseState;

class DOMPreserveTransformerTest {

    @Test
    void testWriteRead() {
        var t = new DOMPreserveTransformer();
        t.setParser("xml");

        var extract1 = new DOMExtractDetails("someTag", "text");
        t.addDOMExtractDetails(extract1);
        var extract2 = new DOMExtractDetails()
                .setSelector("otherTag")
                .setExtract("html");
        t.addDOMExtractDetails(extract2);
        t.setSourceCharset(StandardCharsets.ISO_8859_1.toString());

        Assertions.assertEquals(2, t.getDOMExtractDetailsList().size());
        Assertions.assertNotSame(extract1, extract2);
        Assertions.assertNotEquals(extract1.toString(), extract2.toString());
        Assertions.assertDoesNotThrow(() -> XML.assertWriteRead(t, "handler"));

        t.removeDOMExtractDetails(extract1.getSelector());
        Assertions.assertEquals(1, t.getDOMExtractDetailsList().size());
        t.removeDOMExtractDetailsList();
        Assertions.assertEquals(0, t.getDOMExtractDetailsList().size());
    }


    @Test
    void testTransform() throws ImporterHandlerException, IOException {
        var t = new DOMPreserveTransformer();
        t.setParser("xml");

        // Test batch #1
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: tag text
                "parentA > childA1", "text"));
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: attribute
                "parentB > childB1", "attr(name)"));
        t.addDOMExtractDetails(new DOMExtractDetails( // no match: use default
                "parentD > childD1").setDefaultValue("Child D1"));
        t.addDOMExtractDetails(new DOMExtractDetails( // no match: no default
                "parentE > childE1"));
        Assertions.assertEquals("Child A1\nchild1\nChild D1", transform(t));

        // Test batch #2
        t.removeDOMExtractDetailsList();
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: tag html
                "childA2", "html"));
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: tag outerHtml
                "childA2", "outerHtml"));
        Assertions.assertEquals("<extra>Child A2</extra>\n"
                + "<childA2><extra>Child A2</extra></childA2>", transform(t));

        // Test batch #3
        t.removeDOMExtractDetailsList();
        t.addDOMExtractDetails(new DOMExtractDetails( // no match: ownText
                "parentA", "ownText"));
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: data
                "parentB", "data"));
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: ownText
                "parentC", "ownText"));
        Assertions.assertEquals(
                "I'm Data\nParent C Before Parent C After", transform(t));

        // Test batch #4
        t.removeDOMExtractDetailsList();
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: tagName
                "[name=child1]", "tagName"));
        t.addDOMExtractDetails(new DOMExtractDetails( // preserve: cssSelector
                "childC", "cssSelector"));
        Assertions.assertEquals("""
        	childB1
        	childC
        	DOMTransformerTest > parentC > childC:nth-child(1)
        	DOMTransformerTest > parentC > childC:nth-child(2)""",
                transform(t));
    }

    private static String transform(DOMPreserveTransformer t)
            throws IOException, ImporterHandlerException {
        try (var content =
                ResourceLoader.getXmlStream(DOMPreserveTransformerTest.class);
                var os = new ByteArrayOutputStream()) {
            var metadata = new Properties();
            metadata.set(DocMetadata.CONTENT_TYPE, "application/xml");
            t.transformDocument(TestUtil.newHandlerDoc(
                    "n/a", content, metadata), content, os, ParseState.PRE);
            return os.toString(UTF_8.toString());
        }
    }
}
