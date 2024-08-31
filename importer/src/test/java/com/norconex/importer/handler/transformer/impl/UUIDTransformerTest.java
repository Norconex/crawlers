/* Copyright 2017-2024 Norconex Inc.
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

import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class UUIDTransformerTest {

    @Test
    void testWriteRead() {
        var t = new UUIDTransformer();
        t.getConfiguration()
                .setToField("field1")
                .setOnSet(PropertySetter.REPLACE);
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }

    @Test
    void testUUIDTagger() throws IOException {
        var t = new UUIDTransformer();
        t.getConfiguration()
                .setToField("result");
        var props = new Properties();
        t.accept(
                TestUtil.newHandlerContext(
                        "ref", nullInputStream(), props, ParseState.POST));
        assertThat(props.getStrings("result")).isNotEmpty();
    }
}
