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
package com.norconex.importer.handler.tagger.impl;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.text.TextMatcher.Method;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.impl.TextBetweenTagger.TextBetweenDetails;
import com.norconex.importer.parser.ParseState;

class TextBetweenTaggerTest {

    @Test
    void testExtractFromMetadata()
            throws ImporterHandlerException {
        // use it in a way that one of the end point is all we want to match
        var t = new TextBetweenTagger();
        addDetails(t, "target", "x", "y", false, false, null)
               .getFieldMatcher().setPattern("fld*").setMethod(Method.WILDCARD);

        var metadata = new Properties();
        metadata.add("fld1", "x1y", "x2y", "x3y");
        metadata.add("fld2", "asdfx4yqwer", "asdfx5yquer");
        metadata.add("fld3", "x6y");
        metadata.add("fld4", "7"); //ignored
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");

        TestUtil.tag(t, "n/a", metadata, ParseState.PRE);

        var targetValues = metadata.getStrings("target");
        Collections.sort(targetValues);
        var target = StringUtils.join(targetValues, ",");
        Assertions.assertEquals("1,2,3,4,5,6", target);
    }

    @Test
    void testExtractMatchingRegex()
            throws ImporterHandlerException, IOException {
        // use it in a way that one of the end point is all we want to match
        var t = new TextBetweenTagger();
        addDetails(t, "field", "http://www\\..*?02a\\.gif", "\\b",
                true, false, null);

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");

        t.tagDocument(TestUtil.newHandlerDoc(
                htmlFile.getAbsolutePath(), is, metadata), is, ParseState.PRE);

        is.close();

        var field = metadata.getString("field");

        Assertions.assertEquals("http://www.cs.cmu.edu/%7Ergs/alice02a.gif", field);
    }

    @Test
    void testTagTextDocument()
            throws ImporterHandlerException, IOException {
        var t = new TextBetweenTagger();

        addDetails(t, "headings", "<h1>", "</H1>", true, false, null);
        addDetails(t, "headings", "<h2>", "</H2>", true, false, null);
        addDetails(t, "strong", "<b>", "</B>", true, false, null);
        addDetails(t, "strong", "<i>", "</I>", true, false, null);

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.tagDocument(TestUtil.newHandlerDoc(
                htmlFile.getAbsolutePath(), is, metadata), is, ParseState.PRE);

        is.close();

        var headings = metadata.getStrings("headings");
        var strong = metadata.getStrings("strong");

        Assertions.assertTrue(
                headings.contains("<h2>Down the Rabbit-Hole</h2>"),
                "Failed to return: <h2>Down the Rabbit-Hole</h2>");
        Assertions.assertTrue(headings.contains("<h2>CHAPTER I</h2>"),
                "Failed to return: <h2>CHAPTER I</h2>");
        Assertions.assertTrue(strong.size() == 17,
                "Should have returned 17 <i> and <b> pairs");
    }

    @Test
    void testExtractFirst100ContentChars()
            throws ImporterHandlerException, IOException {
        var t = new TextBetweenTagger();

        addDetails(t, "mytitle", "^", ".{0,100}", true, false, null);
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
        var tagger = new TextBetweenTagger();
        addDetails(tagger, "name", "start", "end", true,
                true, PropertySetter.PREPEND);
        addDetails(tagger, "headingsame", "<h1>", "</h1>", false,
                false, PropertySetter.APPEND);
        tagger.setMaxReadSize(512);
        XML.assertWriteRead(tagger, "handler");
    }

    private static TextBetweenDetails addDetails(
            TextBetweenTagger t, String toField, String start, String end,
            boolean inclusive, boolean caseSensitive, PropertySetter onSet) {
        var tbd = new TextBetweenDetails();
        tbd.setToField(toField);
        tbd.setStartMatcher(
                TextMatcher.regex(start).setIgnoreCase(!caseSensitive));
        tbd.setEndMatcher(TextMatcher.regex(end).setIgnoreCase(!caseSensitive));
        tbd.setInclusive(inclusive);
        tbd.setOnSet(onSet);
        t.addTextBetweenDetails(tbd);
        return tbd;
    }
}
