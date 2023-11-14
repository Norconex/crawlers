/* Copyright 2014-2023 Norconex Inc.
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

import static com.norconex.commons.lang.map.PropertySetter.OPTIONAL;
import static com.norconex.commons.lang.map.PropertySetter.PREPEND;
import static com.norconex.commons.lang.map.PropertySetter.REPLACE;
import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class CopyTransformerTest {

    @Test
    void testWriteRead() {
        var t = new CopyTransformer();
        t.getConfiguration().setOperations(List.of(
            CopyOperation.of(new TextMatcher("from1"), "to1", OPTIONAL),
            CopyOperation.of(new TextMatcher("from2"), "to2")
        ));
        BeanMapper.DEFAULT.assertWriteRead(t);
    }

    @Test
    void testCopyTagger() throws IOException {
        var t = new CopyTransformer();
        t.getConfiguration().setOperations(List.of(
            CopyOperation.of(new TextMatcher("src1"), "trgt1"),
            CopyOperation.of(new TextMatcher("src2"), "trgt2", OPTIONAL),
            CopyOperation.of(new TextMatcher("src3"), "trgt3", PREPEND),
            CopyOperation.of(new TextMatcher("src4"), "trgt4", REPLACE)
        ));

        var props = new Properties();
        props.add("src1", "srcVal1");
        props.add("src2", "srcVal2");
        props.add("src3", "srcVal3");
        props.add("src4", "srcVal4");
        props.add("trgt1", "trgtVal1");
        props.add("trgt2", "trgtVal2");
        props.add("trgt3", "trgtVal3");
        props.add("trgt4", "trgtVal4");

        var docCtx = TestUtil.newDocContext(
                "ref", nullInputStream(), props, ParseState.POST);
        t.accept(docCtx);

        assertThat(props.getStrings("trgt1"))
            .containsExactly("trgtVal1", "srcVal1");
        assertThat(props.getStrings("trgt2")).containsExactly("trgtVal2");
        assertThat(props.getStrings("trgt3"))
            .containsExactly("srcVal3", "trgtVal3");
        assertThat(props.getStrings("trgt4")).containsExactly("srcVal4");
    }

    @Test
    void testOnBody() throws IOException {

        var t = new CopyTransformer();
        t.getConfiguration().setOperations(
                List.of(CopyOperation.of("toField")));

        var body = "Copy this.".getBytes();
        var props = new Properties();
        t.accept(TestUtil.newDocContext(
                "blah", new ByteArrayInputStream(body), props));

        assertThat(props.getString("toField")).isEqualTo("Copy this.");
    }
}
