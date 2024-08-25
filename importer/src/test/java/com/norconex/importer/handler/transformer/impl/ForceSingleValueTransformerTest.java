/* Copyright 2010-2024 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import java.io.IOException;

class ForceSingleValueTransformerTest {

    @Test
    void testWriteRead() {
        var t = new ForceSingleValueTransformer();
        t.getConfiguration()
                .setAction("keepFirst")
                .getFieldMatcher().setPattern("field1|field2|field3");
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(t)
        );
    }

    @Test
    void testForceSingleValueTagger() throws IOException {

        var t = new ForceSingleValueTransformer();
        t.getConfiguration().setFieldMatcher(TextMatcher.basic("a"));

        var props = new Properties();
        props.add("a", 1, 2, 3);
        t.getConfiguration().setAction("keepFirst");
        t.accept(TestUtil.newDocContext("ref", nullInputStream(), props, PRE));
        assertThat(props.getIntegers("a")).containsExactly(1);

        props = new Properties();
        props.add("a", 1, 2, 3);
        t.getConfiguration().setAction("keepLast");
        t.accept(TestUtil.newDocContext("ref", nullInputStream(), props, PRE));
        assertThat(props.getIntegers("a")).containsExactly(3);

        props = new Properties();
        props.add("a", 1, 2, 3);
        t.getConfiguration().setAction("mergeWith:-");
        t.accept(TestUtil.newDocContext("ref", nullInputStream(), props, PRE));
        assertThat(props.getString("a")).isEqualTo("1-2-3");
    }
}
