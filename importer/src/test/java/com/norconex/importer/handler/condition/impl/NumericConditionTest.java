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
package com.norconex.importer.handler.condition.impl;

import static com.norconex.importer.parser.ParseState.PRE;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Operator;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;

class NumericConditionTest {

    @Test
    void testAcceptDocument() throws ImporterHandlerException {

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

        var cond = new NumericCondition();
        cond.setValueMatcher(new NumericCondition.ValueMatcher(
                Operator.GREATER_EQUAL, 20));
        cond.setValueMatcherRangeEnd(new NumericCondition.ValueMatcher(
                Operator.LOWER_THAN, 30));
        cond.setFieldMatcher(TextMatcher.basic("lowerthan"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isFalse();

        cond.setFieldMatcher(TextMatcher.basic("inrange"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();

        cond.setFieldMatcher(TextMatcher.basic("greaterthan"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isFalse();

        cond.setFieldMatcher(TextMatcher.basic("multivalInrange"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();

        cond.setFieldMatcher(TextMatcher.basic("multivalOutrange"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isFalse();

        cond.setValueMatcher(new NumericCondition.ValueMatcher(
                Operator.EQUALS, 6.5));
        cond.setFieldMatcher(TextMatcher.basic("equal"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();
    }

    @Test
    void testWriteRead() {
        var cond = new NumericCondition(
                TextMatcher.basic("field1"),
                new NumericCondition.ValueMatcher(Operator.GREATER_EQUAL, 20),
                new NumericCondition.ValueMatcher(Operator.LOWER_THAN, 30));
        XML.assertWriteRead(cond, "condition");
    }
}
