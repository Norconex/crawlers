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

import static com.norconex.importer.handler.parser.ParseState.PRE;
import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.TestUtil;

class DebugTransformerTest {

    @Test
    void testDebugTagger() {

        var t = new DebugTransformer();
        var cfg = t.getConfiguration();
        cfg.setLogContent(true);
        cfg.setLogFields(List.of("field1", "field2"));
        cfg.setLogLevel("debug");
        cfg.setPrefix("prefix-");

        assertThatNoException().isThrownBy(() -> {
            BeanMapper.DEFAULT.assertWriteRead(t);
        });

        var props = new Properties();
        props.set("field1", "value1");
        props.set("field2", "value2");
        assertThatNoException().isThrownBy(() -> {
            t.accept(
                    TestUtil.newHandlerContext(
                            "ref", nullInputStream(), props, PRE
                    )
            );
        });
    }
}
