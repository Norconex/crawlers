/* Copyright 2016-2023 Norconex Inc.
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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class MergeTransformerTest {

    @Test
    void testWriteRead() {
        var t = new MergeTransformer();
        t.getConfiguration().setOperations(List.of(
                new MergeOperation()
                    .setDeleteFromFields(true)
                    .setFieldMatcher(TextMatcher.regex("(1|2)"))
                    .setSingleValue(true)
                    .setSingleValueSeparator(",")
                    .setToField("toField"),
                new MergeOperation()
                    .setDeleteFromFields(false)
                    .setFieldMatcher(TextMatcher.regex("(3|4)"))
                    .setSingleValue(false)
                    .setSingleValueSeparator(null)
                    .setToField("toAnotherField")
                ));
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(t));
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

        var t = new MergeTransformer();
        t.getConfiguration().setOperations(List.of(
                new MergeOperation()
                    .setDeleteFromFields(false)
                    .setFieldMatcher(TextMatcher.regex("(fld4|fld6|field.*)"))
                    .setSingleValue(false)
                    .setToField("toField"),
                new MergeOperation()
                    .setDeleteFromFields(true)
                    .setFieldMatcher(TextMatcher.regex("(fld4|fld6|field.*)"))
                    .setSingleValue(true)
                    .setSingleValueSeparator("-")
                    .setToField("fld4")
                ));


        TestUtil.transform(t, "n/a", meta, ParseState.POST);

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

        var t = new MergeTransformer();
        t.getConfiguration().setOperations(List.of(
                new MergeOperation()
                    .setDeleteFromFields(false)
                    .setFieldMatcher(TextMatcher.basic("field"))
                    .setSingleValue(true)
                    .setToField("field")
        ));

        TestUtil.transform(t, "n/a", meta, ParseState.POST);

        Assertions.assertEquals("12", meta.getString("field"));
    }
}
