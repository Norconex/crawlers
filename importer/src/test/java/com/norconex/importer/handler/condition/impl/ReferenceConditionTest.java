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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class ReferenceConditionTest {

    @Test
    void testAcceptDocument()
            throws ImporterHandlerException {
        var meta = new Properties();
        var cond = new ReferenceCondition();

        cond.setValueMatcher(TextMatcher.regex(".*/login.*"));

        assertThat(TestUtil.condition(cond,
                "http://www.example.com/login", null, meta, ParseState.PRE))
                    .isTrue();

        assertThat(TestUtil.condition(cond,
                "http://www.example.com/blah", null, meta, ParseState.PRE))
                    .isFalse();
    }

    @Test
    void testWriteRead() {
        var cond = new ReferenceCondition(TextMatcher.regex("blah"));
        XML.assertWriteRead(cond, "condition");
    }
}
