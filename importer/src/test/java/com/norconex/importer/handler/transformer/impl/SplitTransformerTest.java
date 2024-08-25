/* Copyright 2010-2023 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class SplitTransformerTest {

    @Test
    void testWriteRead() {
        var t = new SplitTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new SplitOperation()
                                .setFieldMatcher(new TextMatcher("fromName1"))
                                .setToField("toName1")
                                .setSeparator("sep1")
                                .setSeparatorRegex(false),

                        new SplitOperation()
                                .setFieldMatcher(new TextMatcher("fromName2"))
                                .setToField("toName2")
                                .setSeparator("sep2")
                                .setSeparatorRegex(true),

                        new SplitOperation()
                                .setFieldMatcher(new TextMatcher("fromName3"))
                                .setSeparator("sep3")
                                .setSeparatorRegex(true),

                        new SplitOperation()
                                .setFieldMatcher(new TextMatcher("fromName4"))
                                .setSeparator("sep4")
                                .setSeparatorRegex(false),

                        new SplitOperation()
                                .setFieldMatcher(new TextMatcher("fromName5"))
                                .setToField("toName5")
                                .setSeparator("sep5")
                                .setSeparatorRegex(true)
                                .setOnSet(PropertySetter.OPTIONAL)
                )
        );

        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }

    @Test
    void testMetadataRegularSplit() throws IOException {
        var meta = new Properties();
        meta.add("metaToSplitSameField", "Joe, Jack, William, Avrel");
        meta.add("metaNoSplitSameField", "Joe Jack William Avrel");
        meta.add("metaToSplitNewField", "Joe, Jack, William, Avrel");
        meta.add("metaNoSplitNewField", "Joe Jack William Avrel");
        meta.add(
                "metaMultiSameField",
                "Joe, Jack", "William, Avrel"
        );
        meta.add(
                "metaMultiNewField",
                "Joe, Jack", "William, Avrel"
        );

        var t = new SplitTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new SplitOperation()
                                .setFieldMatcher(
                                        new TextMatcher("metaToSplitSameField")
                                )
                                .setSeparator(", ")
                                .setSeparatorRegex(false),

                        new SplitOperation()
                                .setFieldMatcher(
                                        new TextMatcher("metaNoSplitSameField")
                                )
                                .setSeparator(", ")
                                .setSeparatorRegex(false),

                        new SplitOperation()
                                .setFieldMatcher(
                                        new TextMatcher("metaToSplitNewField")
                                )
                                .setToField("toSplitNewField")
                                .setSeparator(", ")
                                .setSeparatorRegex(false),

                        new SplitOperation()
                                .setFieldMatcher(
                                        new TextMatcher("metaNoSplitNewField")
                                )
                                .setToField("noSplitNewField")
                                .setSeparator(", ")
                                .setSeparatorRegex(false),

                        new SplitOperation()
                                .setFieldMatcher(
                                        new TextMatcher("metaMultiSameField")
                                )
                                .setSeparator(", ")
                                .setSeparatorRegex(false),

                        new SplitOperation()
                                .setFieldMatcher(
                                        new TextMatcher("metaMultiNewField")
                                )
                                .setToField("multiNewField")
                                .setSeparator(", ")
                                .setSeparatorRegex(false)
                )
        );

        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        List<String> toSplitExpect = Arrays.asList(
                "Joe", "Jack", "William", "Avrel"
        );
        List<String> noSplitExpect = Arrays.asList(
                "Joe Jack William Avrel"
        );

        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("metaToSplitSameField")
        );
        Assertions.assertEquals(
                noSplitExpect, meta.getStrings("metaNoSplitSameField")
        );
        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("toSplitNewField")
        );
        Assertions.assertEquals(
                noSplitExpect, meta.getStrings("metaNoSplitNewField")
        );
        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("metaMultiSameField")
        );
        Assertions.assertEquals(
                toSplitExpect, meta.getStrings("multiNewField")
        );

        Assertions.assertEquals(
                noSplitExpect, meta.getStrings("metaNoSplitSameField")
        );
    }

    @Test
    void testMetadataRegexSplit() throws IOException {
        var meta = new Properties();
        meta.add("path1", "/a/path/file.doc");
        meta.add("path2", "a, b,c d;e, f");

        var t = new SplitTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new SplitOperation()
                                .setFieldMatcher(new TextMatcher("path1"))
                                .setSeparator("/")
                                .setSeparatorRegex(true),

                        new SplitOperation()
                                .setFieldMatcher(new TextMatcher("path2"))
                                .setToField("file2")
                                .setSeparator("[, ;]+")
                                .setSeparatorRegex(true)
                )
        );

        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals(
                Arrays.asList("a", "path", "file.doc"),
                meta.getStrings("path1")
        );
        Assertions.assertEquals(
                Arrays.asList("a", "b", "c", "d", "e", "f"),
                meta.getStrings("file2")
        );
    }

    @Test
    void testContentRegularSplit() throws IOException {
        var meta = new Properties();

        var t = new SplitTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new SplitOperation()
                                .setToField("targetField")
                                .setSeparator(", ")
                )
        );

        var is = IOUtils.toInputStream(
                "Joe, Jack, William, Avrel", StandardCharsets.UTF_8
        );
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));

        Assertions.assertEquals(
                Arrays.asList("Joe", "Jack", "William", "Avrel"),
                meta.getStrings("targetField")
        );
    }

    @Test
    void testContentRegexSplit() throws IOException {
        var meta = new Properties();
        var t = new SplitTransformer();
        t.getConfiguration().setOperations(
                List.of(
                        new SplitOperation()
                                .setToField("targetField")
                                .setSeparator("[, ;]+")
                                .setSeparatorRegex(true)
                )
        );

        var is = IOUtils.toInputStream("a, b,c d;e, f", StandardCharsets.UTF_8);
        t.accept(TestUtil.newDocContext("n/a", is, meta, ParseState.POST));
        Assertions.assertEquals(
                Arrays.asList("a", "b", "c", "d", "e", "f"),
                meta.getStrings("targetField")
        );
    }
}
