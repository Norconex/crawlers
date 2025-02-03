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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class DocumentLengthTransformerTest {

    @Test
    void testDocumentLengthTagger() throws IOException {
        var t = new DocumentLengthTransformer();
        t.getConfiguration()
                .setToField("theLength")
                .setOnSet(PropertySetter.REPLACE);

        assertThatNoException().isThrownBy(() -> {
            BeanMapper.DEFAULT.assertWriteRead(t);
        });

        var props = new Properties();
        assertThatNoException().isThrownBy(() -> {
            t.handle(
                    TestUtil.newHandlerContext(
                            "ref",
                            TestUtil.toCachedInputStream("four"),
                            props,
                            ParseState.PRE));
        });
        assertThat(props.getLong("theLength")).isEqualTo(4);

        assertThatNoException().isThrownBy(() -> {
            t.handle(
                    TestUtil.newHandlerContext(
                            "ref",
                            TestUtil.toInputStream("fives"),
                            props,
                            ParseState.PRE));
        });
        assertThat(props.getLong("theLength")).isEqualTo(5);
    }
}
