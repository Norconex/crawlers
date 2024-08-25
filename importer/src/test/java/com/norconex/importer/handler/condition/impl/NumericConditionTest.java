/* Copyright 2022-2024 Norconex Inc.
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

import static com.norconex.importer.handler.parser.ParseState.PRE;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.Operator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;

class NumericConditionTest {

    @Test
    void testAcceptDocument() throws IOException {

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
        var cfg = cond.getConfiguration();
        cfg.setValueMatcher(
                new NumericValueMatcher(
                        Operator.GREATER_EQUAL, 20
                )
        );
        cfg.setValueMatcherRangeEnd(
                new NumericValueMatcher(
                        Operator.LOWER_THAN, 30
                )
        );
        cfg.setFieldMatcher(TextMatcher.basic("lowerthan"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isFalse();

        cfg.setFieldMatcher(TextMatcher.basic("inrange"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();

        cfg.setFieldMatcher(TextMatcher.basic("greaterthan"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isFalse();

        cfg.setFieldMatcher(TextMatcher.basic("multivalInrange"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();

        cfg.setFieldMatcher(TextMatcher.basic("multivalOutrange"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isFalse();

        cfg.setValueMatcher(
                new NumericValueMatcher(
                        Operator.EQUALS, 6.5
                )
        );
        cfg.setFieldMatcher(TextMatcher.basic("equal"));
        assertThat(TestUtil.condition(cond, "n/a", null, meta, PRE)).isTrue();
    }

    @Test
    void testWriteRead() {
        var cond = Configurable.configure(new NumericCondition(), cfg -> {
            cfg
                    .setFieldMatcher(TextMatcher.basic("field1"))
                    .setValueMatcher(
                            new NumericValueMatcher(Operator.GREATER_EQUAL, 20)
                    )
                    .setValueMatcherRangeEnd(
                            new NumericValueMatcher(Operator.LOWER_THAN, 30)
                    );
        });
        BeanMapper.DEFAULT.assertWriteRead(cond);
    }
}
