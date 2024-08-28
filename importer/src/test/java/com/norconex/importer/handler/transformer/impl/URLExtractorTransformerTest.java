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
package com.norconex.importer.handler.transformer.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class URLExtractorTransformerTest {

    @Test
    void testWriteRead() {
        var t = new URLExtractorTransformer();
        t.getConfiguration()
                .setFieldMatcher(TextMatcher.basic("blah"))
                .setMaxReadSize(10)
                .setOnSet(PropertySetter.REPLACE)
                .setSourceCharset(UTF_8)
                .setToField("there");

        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(t)
        );
    }

    @Test
    void testURLExtractorTagger() throws IOException {
        var t = new URLExtractorTransformer();
        t.getConfiguration()
                .setToField("result");

        var text = """
                This is a sample http://example.com/ url to be extracted.
                This is another one www.example.com/blah.html.
                """;

        var props = new Properties();
        t.accept(
                TestUtil.newHandlerContext(
                        "ref", TestUtil.toInputStream(text), props,
                        ParseState.POST
                )
        );
        assertThat(props.getStrings("result")).containsExactlyInAnyOrder(
                "http://example.com/",
                "https://www.example.com/blah.html"
        ); // https is prepended
    }
}
