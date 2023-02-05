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
package com.norconex.importer.handler.tagger.impl;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class RegexTaggerTest {

    @Test
    void testTagTextDocument()
            throws ImporterHandlerException, IOException {
        var t = new RegexTagger();
        t.addPattern("headings", "<h2>(.*?)</h2>" , 1);
        t.addPattern("country", "\\w+\\sZealand");
        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.tagDocument(TestUtil.newHandlerDoc(
                htmlFile.getAbsolutePath(), is, metadata), is, ParseState.PRE);

        is.close();

        var headings = metadata.getStrings("headings");
        var countries = metadata.getStrings("country");

        Assertions.assertEquals(2, headings.size(), "Wrong <h2> count.");
        Assertions.assertEquals("CHAPTER I", headings.get(0),
                "Did not extract first heading");
        Assertions.assertEquals("Down the Rabbit-Hole", headings.get(1),
                "Did not extract second heading");

        Assertions.assertEquals(1, countries.size(), "Wrong country count.");
        Assertions.assertEquals("New Zealand", countries.get(0),
                "Did not extract country");
    }

    @Test
    void testExtractFirst100ContentChars()
            throws ImporterHandlerException, IOException {
        var t = new RegexTagger();
        t.addPattern("mytitle", "^.{0,100}");
        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.tagDocument(TestUtil.newHandlerDoc(
                htmlFile.getAbsolutePath(), is, metadata), is, ParseState.PRE);

        is.close();

        var myTitle = metadata.getString("mytitle");
        Assertions.assertEquals(100, myTitle.length());
    }

    @Test
    void testWriteRead() {
        var tagger = new RegexTagger();
        tagger.addPattern("field1", "123.*890");
        tagger.addPattern("field2", "abc.*xyz", 3);
        tagger.addPattern(new RegexFieldValueExtractor("blah")
                .setToField("field3")
                .setFieldGroup(3)
                .setValueGroup(6)
                .setOnSet(PropertySetter.PREPEND));
        tagger.setMaxReadSize(512);
        XML.assertWriteRead(tagger, "handler");
    }
}
