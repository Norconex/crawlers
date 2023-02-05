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
package com.norconex.importer.handler.transformer.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.ByteArrayOutputStream;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.TestUtil;
import com.norconex.importer.parser.ParseState;

class NoContentTransformerTest {

    @Test
    void testWriteRead() {
        var t = new NoContentTransformer();
        t.setToField("myfield");
        t.setOnSet(PropertySetter.REPLACE);
        assertThatNoException().isThrownBy(() ->
            XML.assertWriteRead(t, "handler"));
        assertThat(t.getOnSet()).isSameAs(PropertySetter.REPLACE);
        assertThat(t.getToField()).isEqualTo("myfield");
    }

    @Test
    void testNoContentTransformer() {
        var t = new NoContentTransformer();
        t.setToField("myfield");
        t.setOnSet(PropertySetter.REPLACE);

        var props = new Properties();
        var out = new ByteArrayOutputStream();
        assertThatNoException().isThrownBy(() ->
            t.transformApplicableDocument(
                    TestUtil.newHandlerDoc(props),
                    TestUtil.toInputStream("some content"),
                    out,
                    ParseState.POST));
        assertThat(out.size()).isZero();
        assertThat(props.getString("myfield")).isEqualTo("some content");
    }
}
