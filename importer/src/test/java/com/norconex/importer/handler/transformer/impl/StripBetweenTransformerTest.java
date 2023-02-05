/* Copyright 2010-2022 Norconex Inc.
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

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.impl.StripBetweenTransformer.StripBetweenDetails;
import com.norconex.importer.parser.ParseState;

class StripBetweenTransformerTest {

    @Test
    void testTransformTextDocument()
            throws ImporterHandlerException, IOException {
        var t = new StripBetweenTransformer();
        addEndPoints(t, "<h2>", "</h2>");
        addEndPoints(t, "<P>", "</P>");
        addEndPoints(t, "<head>", "</hEad>");
        addEndPoints(t, "<Pre>", "</prE>");

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var os = new ByteArrayOutputStream();
        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.transformDocument(
                TestUtil.newHandlerDoc(htmlFile.getAbsolutePath(), is, metadata),
                is, os, ParseState.PRE);

        Assertions.assertEquals(458, os.toString().length(),
                "Length of doc content after transformation is incorrect.");

        is.close();
        os.close();
    }


    @Test
    void testCollectorHttpIssue237()
            throws ImporterHandlerException, IOException {
        var t = new StripBetweenTransformer();
        addEndPoints(t, "<body>", "<\\!-- START -->");
        addEndPoints(t, "<\\!-- END -->", "<\\!-- START -->");
        addEndPoints(t, "<\\!-- END -->", "</body>");

        var html = """
        	<html><body>\
        	ignore this text\
        	<!-- START -->extract me 1<!-- END -->\
        	ignore this text\
        	<!-- START -->extract me 2<!-- END -->\
        	ignore this text\
        	</body></html>""";

        var is = new ByteArrayInputStream(html.getBytes());
        var os = new ByteArrayOutputStream();
        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.transformDocument(TestUtil.newHandlerDoc("fake.html", is, metadata),
                is, os, ParseState.PRE);
        var output = os.toString();
        is.close();
        os.close();
        //System.out.println(output);
        Assertions.assertEquals(
                "<html>extract me 1extract me 2</html>", output);
    }


    @Test
    void testWriteRead() {
        var t = new StripBetweenTransformer();
        addEndPoints(t, "<!-- NO INDEX", "/NOINDEX -->");
        addEndPoints(t, "<!-- HEADER START", "HEADER END -->");
        XML.assertWriteRead(t, "handler");
    }

    private void addEndPoints(
            StripBetweenTransformer t, String start, String end) {
        var d = new StripBetweenDetails(
                TextMatcher.regex(start).setIgnoreCase(true),
                TextMatcher.regex(end).setIgnoreCase(true));
        d.setInclusive(true);
        t.addStripBetweenDetails(d);
    }
}
