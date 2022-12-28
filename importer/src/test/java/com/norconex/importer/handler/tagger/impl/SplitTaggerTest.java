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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.impl.SplitTagger.SplitDetails;
import com.norconex.importer.parser.ParseState;

class SplitTaggerTest {

    @Test
    void testWriteRead() {
        var tagger = new SplitTagger();
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("fromName1"), "toName1", "sep1", false));
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("fromName2"), "toName2", "sep2", true));
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("fromName3"), null, "sep3", true));
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("fromName4"), null, "sep4", false));
        var sp = new SplitDetails(
                new TextMatcher("fromName5"), "toName5", "sep5", true);
        sp.setOnSet(PropertySetter.OPTIONAL);
        tagger.addSplitDetails(sp);

        XML.assertWriteRead(tagger, "handler");
    }

    @Test
    void testMetadataRegularSplit() throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("metaToSplitSameField", "Joe, Jack, William, Avrel");
        meta.add("metaNoSplitSameField", "Joe Jack William Avrel");
        meta.add("metaToSplitNewField", "Joe, Jack, William, Avrel");
        meta.add("metaNoSplitNewField", "Joe Jack William Avrel");
        meta.add("metaMultiSameField",
                "Joe, Jack", "William, Avrel");
        meta.add("metaMultiNewField",
                "Joe, Jack", "William, Avrel");


        var tagger = new SplitTagger();
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("metaToSplitSameField"), null, ", ", false));
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("metaNoSplitSameField"), null, ", ", false));
        tagger.addSplitDetails(new SplitDetails(new TextMatcher(
                "metaToSplitNewField"), "toSplitNewField", ", ", false));
        tagger.addSplitDetails(new SplitDetails(new TextMatcher(
                "metaNoSplitNewField"), "noSplitNewField", ", ", false));
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("metaMultiSameField"), null, ", ", false));
        tagger.addSplitDetails(new SplitDetails(new TextMatcher(
                "metaMultiNewField"), "multiNewField", ", ", false));

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        List<String> toSplitExpect = Arrays.asList(
                "Joe", "Jack", "William", "Avrel");
        List<String> noSplitExpect = Arrays.asList(
                "Joe Jack William Avrel");

        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("metaToSplitSameField"));
        Assertions.assertEquals(
                noSplitExpect, meta.getStrings("metaNoSplitSameField"));
        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("toSplitNewField"));
        Assertions.assertEquals(
                noSplitExpect, meta.getStrings("metaNoSplitNewField"));
        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("metaMultiSameField"));
        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("multiNewField"));

        Assertions.assertEquals(
                noSplitExpect, meta.getStrings("metaNoSplitSameField"));
    }

    @Test
    void testMetadataRegexSplit() throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("path1", "/a/path/file.doc");
        meta.add("path2", "a, b,c d;e, f");

        var tagger = new SplitTagger();
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("path1"), null, "/", true));
        tagger.addSplitDetails(new SplitDetails(
                new TextMatcher("path2"), "file2", "[, ;]+", true));

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals(
                Arrays.asList("a", "path", "file.doc"),
                meta.getStrings("path1"));
        Assertions.assertEquals(
                Arrays.asList("a", "b", "c", "d", "e", "f"),
                meta.getStrings("file2"));
    }

    @Test
    void testContentRegularSplit() throws ImporterHandlerException {
        var meta = new Properties();

        var tagger = new SplitTagger();

        tagger.addSplitDetails(new SplitDetails(
                (TextMatcher) null, "targetField", ", ", false));

        var is = IOUtils.toInputStream(
                "Joe, Jack, William, Avrel", StandardCharsets.UTF_8);
        tagger.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);

        Assertions.assertEquals(
                Arrays.asList("Joe", "Jack", "William", "Avrel"),
                meta.getStrings("targetField"));
    }

    @Test
    void testContentRegexSplit() throws ImporterHandlerException {
        var meta = new Properties();
        var tagger = new SplitTagger();
        tagger.addSplitDetails(new SplitDetails(
                (TextMatcher) null, "targetField", "[, ;]+", true));


        var is = IOUtils.toInputStream(
                "a, b,c d;e, f", StandardCharsets.UTF_8);
        tagger.tagDocument(
                TestUtil.newHandlerDoc("n/a", is, meta), is, ParseState.POST);
        Assertions.assertEquals(
                Arrays.asList("a", "b", "c", "d", "e", "f"),
                meta.getStrings("targetField"));
    }
}
