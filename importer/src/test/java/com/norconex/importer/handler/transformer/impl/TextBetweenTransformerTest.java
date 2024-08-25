/* Copyright 2010-2024 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.parser.ParseState;

class TextBetweenTransformerTest {

    @Test
    void testExtractFromMetadata() throws IOException {
        // use it in a way that one of the end point is all we want to match
        var t = new TextBetweenTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new TextBetweenOperation()
                                .setToField("target")
                                .setFieldMatcher(TextMatcher.wildcard("fld*"))
                                .setStartMatcher(
                                        TextMatcher.regex("x")
                                                .setIgnoreCase(false)
                                )
                                .setEndMatcher(
                                        TextMatcher.regex("y")
                                                .setIgnoreCase(false)
                                )
                )
        );

        var metadata = new Properties();
        metadata.add("fld1", "x1y", "x2y", "x3y");
        metadata.add("fld2", "asdfx4yqwer", "asdfx5yquer");
        metadata.add("fld3", "x6y");
        metadata.add("fld4", "7"); //ignored
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");

        TestUtil.transform(t, "n/a", metadata, ParseState.PRE);

        var targetValues = metadata.getStrings("target");
        Collections.sort(targetValues);
        var target = StringUtils.join(targetValues, ",");
        Assertions.assertEquals("1,2,3,4,5,6", target);
    }

    @Test
    void testExtractMatchingRegex() throws IOException {
        // use it in a way that one of the end point is all we want to match
        var t = new TextBetweenTransformer();
        addDetails(
                t, "field", "http://www\\..*?02a\\.gif", "\\b",
                true, false, null
        );

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");

        t.accept(
                TestUtil.newDocContext(
                        htmlFile.getAbsolutePath(), is, metadata, ParseState.PRE
                )
        );

        is.close();

        var field = metadata.getString("field");

        Assertions.assertEquals(
                "http://www.cs.cmu.edu/%7Ergs/alice02a.gif", field
        );
    }

    @Test
    void testTagTextDocument() throws IOException {
        var t = new TextBetweenTransformer();

        addDetails(t, "headings", "<h1>", "</H1>", true, true, null);
        addDetails(t, "headings", "<h2>", "</H2>", true, true, null);
        addDetails(t, "strong", "<b>", "</B>", true, true, null);
        addDetails(t, "strong", "<i>", "</I>", true, true, null);

        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.accept(
                TestUtil.newDocContext(
                        htmlFile.getAbsolutePath(), is, metadata, ParseState.PRE
                )
        );

        is.close();

        var headings = metadata.getStrings("headings");
        var strong = metadata.getStrings("strong");

        assertThat(headings).contains(
                "<h2>Down the Rabbit-Hole</h2>",
                "<h2>CHAPTER I</h2>"
        );
        assertThat(strong).size().isEqualTo(17);
    }

    @Test
    void testExtractFirst100ContentChars() throws IOException {
        var t = new TextBetweenTransformer();

        addDetails(t, "mytitle", "^", ".{0,100}", true, false, null);
        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.accept(
                TestUtil.newDocContext(
                        htmlFile.getAbsolutePath(), is, metadata, ParseState.PRE
                )
        );

        is.close();

        var myTitle = metadata.getString("mytitle");
        Assertions.assertEquals(100, myTitle.length());
    }

    @Test
    void testWriteRead() {
        var t = new TextBetweenTransformer();
        addDetails(
                t, "name", "start", "end", true,
                true, PropertySetter.PREPEND
        );
        addDetails(
                t, "headingsame", "<h1>", "</h1>", false,
                false, PropertySetter.APPEND
        );
        t.getConfiguration().setMaxReadSize(512);
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }

    private static void addDetails(
            TextBetweenTransformer t, String toField, String start, String end,
            boolean inclusive, boolean ignoreCase, PropertySetter onSet
    ) {
        var tbd = new TextBetweenOperation();
        tbd.setToField(toField);
        tbd.setStartMatcher(
                TextMatcher.regex(start).setIgnoreCase(ignoreCase)
        );
        tbd.setEndMatcher(TextMatcher.regex(end).setIgnoreCase(ignoreCase));
        tbd.setInclusive(inclusive);
        tbd.setOnSet(onSet);
        List<TextBetweenOperation> ops =
                new ArrayList<>(t.getConfiguration().getOperations());
        ops.add(tbd);
        t.getConfiguration().setOperations(ops);
    }
}
