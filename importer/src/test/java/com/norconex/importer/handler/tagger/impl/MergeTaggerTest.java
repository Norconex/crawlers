/* Copyright 2016-2022 Norconex Inc.
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

import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.impl.MergeTagger.Merge;
import com.norconex.importer.parser.ParseState;

class MergeTaggerTest {

    @Test
    void testWriteRead() {
        var tagger = new MergeTagger();

        var m = new Merge();

        m.setDeleteFromFields(true);
        m.setFieldMatcher(TextMatcher.regex("(1|2)"));
        m.setSingleValue(true);
        m.setSingleValueSeparator(",");
        m.setToField("toField");
        tagger.addMerge(m);

        m = new Merge();
        m.setDeleteFromFields(false);
        m.setFieldMatcher(TextMatcher.regex("(3|4)"));
        m.setSingleValue(false);
        m.setSingleValueSeparator(null);
        m.setToField("toAnotherField");
        tagger.addMerge(m);

        XML.assertWriteRead(tagger, "handler");
    }


    @Test
    void testMultiFieldsMerge() throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("field1", "1.1", "1.2");
        meta.add("field2", "2");
        meta.add("field3", "3");
        meta.add("fld4", "4");
        meta.add("fld5", "5");
        meta.add("fld6", "6");

        var tagger = new MergeTagger();

        var m = new Merge();

        m.setDeleteFromFields(false);
        m.setFieldMatcher(TextMatcher.regex("(fld4|fld6|field.*)"));
        m.setSingleValue(false);
        m.setToField("toField");
        tagger.addMerge(m);

        m = new Merge();
        m.setDeleteFromFields(true);
        m.setFieldMatcher(TextMatcher.regex("(fld4|fld6|field.*)"));
        m.setSingleValue(true);
        m.setSingleValueSeparator("-");
        m.setToField("fld4");
        tagger.addMerge(m);

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Set<String> expected = new TreeSet<>(Arrays.asList(
                "1.1", "1.2", "2", "3", "4", "6"));

        Assertions.assertEquals(expected,
                new TreeSet<>(meta.getStrings("toField")));
        Assertions.assertEquals(expected, new TreeSet<>(Arrays.asList(
                meta.getString("fld4").split("-"))));
    }

    @Test
    void testSingleFieldMerge() throws ImporterHandlerException {
        var meta = new Properties();
        meta.add("field", "1", "2");

        var tagger = new MergeTagger();

        var m = new Merge();
        m.setDeleteFromFields(false);
        m.setFieldMatcher(TextMatcher.basic("field"));
        m.setSingleValue(true);
        m.setToField("field");
        tagger.addMerge(m);

        TestUtil.tag(tagger, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("12", meta.getString("field"));
    }

}
