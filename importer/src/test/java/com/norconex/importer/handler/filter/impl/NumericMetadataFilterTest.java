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
package com.norconex.importer.handler.filter.impl;

import static com.norconex.importer.parser.ParseState.PRE;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Operator;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.OnMatch;

class NumericMetadataFilterTest {

    @Test
    void testAcceptDocument()
            throws ImporterHandlerException {

        var meta = new Properties();
        meta.add("lowerthan", "-4.25");
        meta.add("inrange", "25");
        meta.add("greaterthan", "55");
        meta.add("multivalInrange", "0");
        meta.add("multivalInrange", "20");
        meta.add("multivalInrange", "66");
        meta.add("multivalOutrange", "0");
        meta.add("multivalOutrange", "11");
        meta.add("multivalOutrange", "66");
        meta.add("equal", "6.5");

        var filter = new NumericMetadataFilter();
        filter.addCondition(Operator.GREATER_EQUAL, 20);
        filter.addCondition(Operator.LOWER_THAN, 30);

        filter.setFieldMatcher(TextMatcher.basic("lowerthan"));
        Assertions.assertFalse(
                TestUtil.filter(filter, "n/a", null, meta, PRE));

        filter.setFieldMatcher(TextMatcher.basic("inrange"));
        Assertions.assertTrue(
                TestUtil.filter(filter, "n/a", null, meta, PRE));

        filter.setFieldMatcher(TextMatcher.basic("greaterthan"));
        Assertions.assertFalse(
                TestUtil.filter(filter, "n/a", null, meta, PRE));

        filter.setFieldMatcher(TextMatcher.basic("multivalInrange"));
        Assertions.assertTrue(
                TestUtil.filter(filter, "n/a", null, meta, PRE));

        filter.setFieldMatcher(TextMatcher.basic("multivalOutrange"));
        Assertions.assertFalse(
                TestUtil.filter(filter, "n/a", null, meta, PRE));

        filter.setConditions(new NumericMetadataFilter.Condition(
                Operator.EQUALS, 6.5));
        filter.setFieldMatcher(TextMatcher.basic("equal"));
        Assertions.assertTrue(
                TestUtil.filter(filter, "n/a", null, meta, PRE));
    }

    @Test
        void testWriteRead() {
        var filter = new NumericMetadataFilter(TextMatcher.basic("field1"));
        filter.setOnMatch(OnMatch.EXCLUDE);
        filter.addCondition(Operator.GREATER_EQUAL, 20);
        filter.addCondition(Operator.LOWER_THAN, 30);
        XML.assertWriteRead(filter, "handler");
    }
}
