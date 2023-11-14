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

import static com.norconex.importer.TestUtil.toCachedInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.parser.ParseState;

class BlankConditionTest {

    private CachedInputStream emptyInput = toCachedInputStream("");
    private DocContext docCtx;
    private BlankCondition c;

    @BeforeEach
    void beforeEach() {
        docCtx = newDocContext();
        c = new BlankCondition();
    }

    @Test
    void testBlankContentCondition() throws IOException {
        // Test content

        assertThat(c.evaluate(newDocContext("blah"))).isFalse();

        assertThat(c.evaluate(docCtx)).isTrue();
    }

    @Test
    void testAllBlankFieldsCondition() throws IOException {
        c.getConfiguration().setFieldMatcher(TextMatcher.regex("field.*"));
        assertThat(c.evaluate(docCtx)).isFalse();

        c.getConfiguration().setFieldMatcher(TextMatcher.basic("field3"));
        assertThat(c.evaluate(docCtx)).isFalse();
        c.getConfiguration().setFieldMatcher(TextMatcher.regex("field4\\..*"));
        assertThat(c.evaluate(docCtx)).isFalse();
        c.getConfiguration().setFieldMatcher(
                TextMatcher.regex("field4\\.[123]"));
        assertThat(c.evaluate(docCtx)).isTrue();
        c.getConfiguration().setFieldMatcher(TextMatcher.basic("field4.4"));
        assertThat(c.evaluate(docCtx)).isFalse();
    }

    @Test
    void testAnyBlankFieldsCondition() throws IOException {
        c.getConfiguration().setFieldMatcher(TextMatcher.regex("field.*"));
        c.getConfiguration().setMatchAnyBlank(true);
        assertThat(c.evaluate(docCtx)).isTrue();

        c.getConfiguration().setFieldMatcher(TextMatcher.basic("field3"));
        assertThat(c.evaluate(docCtx)).isFalse();
        c.getConfiguration().setFieldMatcher(TextMatcher.regex("field4\\..*"));
        assertThat(c.evaluate(docCtx)).isTrue();
        c.getConfiguration().setFieldMatcher(
                TextMatcher.regex("field4\\.[123]"));
        assertThat(c.evaluate(docCtx)).isTrue();
        c.getConfiguration().setFieldMatcher(TextMatcher.basic("field4.4"));
        assertThat(c.evaluate(docCtx)).isTrue();
    }

    @Test
    void testMisc() throws IOException {
        // Test non-existant
        c.getConfiguration().setFieldMatcher(TextMatcher.basic("doNotExist"));
        assertThat(c.evaluate(docCtx)).isTrue();

        // Test write read
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(c));
    }


    private DocContext newDocContext() {
        return newDocContext(null);
    }
    private DocContext newDocContext(String body) {
        var props = TestUtil.newMetadata();
        props.add("field4.1", "");
        props.add("field4.2", "    ");
        props.add("field4.3", (String) null);
        props.add("field4.3", "");
        props.add("field4.3", "");
        props.add("field4.4", "value4.4");
        props.add("field4.4", "");

        return DocContext.builder()
                .doc(TestUtil.newDoc("ref",
                    body == null
                    ? emptyInput
                    : toCachedInputStream(body), props))
                .parseState(ParseState.PRE)
                .eventManager(new EventManager())
                .build();
    }
}
