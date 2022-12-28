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
package com.norconex.importer.handler.tagger.impl;

import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.parser.ParseState;

class URLExtractorTaggerTest {

    @Test
    void testWriteRead() {
        var t = new URLExtractorTagger();
        t.setFieldMatcher(TextMatcher.basic("blah"));
        t.setMaxReadSize(10);
        t.setOnSet(PropertySetter.REPLACE);
        t.setSourceCharset("UTF-8");
        t.setToField("there");

        assertThatNoException().isThrownBy(
                () -> XML.assertWriteRead(t, "handler"));
    }

    @Test
    void testURLExtractorTagger() throws ImporterHandlerException {
        var t = new URLExtractorTagger();
        t.setToField("result");

        var text = """
                This is a sample http://example.com/ url to be extracted.
                This is another one www.example.com/blah.html.
                """;

        var props = new Properties();
        t.tagDocument(TestUtil.newHandlerDoc("ref", nullInputStream(), props),
                TestUtil.toInputStream(text), ParseState.POST);
        assertThat(props.getStrings("result")).containsExactlyInAnyOrder(
                "http://example.com/",
                "https://www.example.com/blah.html"); // https is prepended
    }
}
