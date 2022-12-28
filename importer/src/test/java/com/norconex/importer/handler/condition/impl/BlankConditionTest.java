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
import static com.norconex.importer.parser.ParseState.PRE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;

class BlankConditionTest {

    private CachedInputStream emptyInput = toCachedInputStream("");
    private HandlerDoc doc;
    private BlankCondition c;

    @BeforeEach
    void beforeEach() {
        doc = newDoc();
        c = new BlankCondition();
    }

    @Test
    void testBlankContentCondition() throws ImporterHandlerException {
        // Test content
        assertThat(c.testDocument(
                doc, toCachedInputStream("blah"), PRE)).isFalse();
        assertThat(c.testDocument(doc, emptyInput, PRE)).isTrue();
    }

    @Test
    void testAllBlankFieldsCondition() throws ImporterHandlerException {
        c.setFieldMatcher(TextMatcher.regex("field.*"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isFalse();

        c.setFieldMatcher(TextMatcher.basic("field3"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isFalse();
        c.setFieldMatcher(TextMatcher.regex("field4\\..*"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isFalse();
        c.setFieldMatcher(TextMatcher.regex("field4\\.[123]"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isTrue();
        c.setFieldMatcher(TextMatcher.basic("field4.4"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isFalse();
    }

    @Test
    void testAnyBlankFieldsCondition() throws ImporterHandlerException {
        c.setFieldMatcher(TextMatcher.regex("field.*"));
        c.setMatchAnyBlank(true);
        assertThat(c.testDocument(doc, emptyInput, PRE)).isTrue();

        c.setFieldMatcher(TextMatcher.basic("field3"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isFalse();
        c.setFieldMatcher(TextMatcher.regex("field4\\..*"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isTrue();
        c.setFieldMatcher(TextMatcher.regex("field4\\.[123]"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isTrue();
        c.setFieldMatcher(TextMatcher.basic("field4.4"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isTrue();
    }

    @Test
    void testMisc() throws ImporterHandlerException {
        // Test non-existant
        c.setFieldMatcher(TextMatcher.basic("doNotExist"));
        assertThat(c.testDocument(doc, emptyInput, PRE)).isTrue();

        // Test write read
        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(c, "condition"));
    }

    private HandlerDoc newDoc() {
        var props = TestUtil.newMetadata();
        props.add("field4.1", "");
        props.add("field4.2", "    ");
        props.add("field4.3", (String) null);
        props.add("field4.3", "");
        props.add("field4.3", "");
        props.add("field4.4", "value4.4");
        props.add("field4.4", "");

        return TestUtil.newHandlerDoc(
                "ref", toCachedInputStream("content1 content2"), props);
    }
}
