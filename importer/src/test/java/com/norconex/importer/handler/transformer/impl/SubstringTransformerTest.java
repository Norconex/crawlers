/* Copyright 2017-2023 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.ImporterException;
import com.norconex.importer.TestUtil;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class SubstringTransformerTest {

    @Test
    void testTransformTextDocument()
            throws IOException {
        var content = "1234567890";

        Assertions.assertEquals("", substring(0, 0, content));
        Assertions.assertEquals(content, substring(-1, -1, content));
        Assertions.assertEquals("123", substring(0, 3, content));
        Assertions.assertEquals("456", substring(3, 6, content));
        Assertions.assertEquals("890", substring(7, 42, content));
        Assertions.assertEquals("1234", substring(-1, 4, content));
        Assertions.assertEquals("7890", substring(6, -1, content));
        try {
            substring(4, 1, content);
            Assertions.fail("Should have triggered an exception.");
        } catch (ImporterException e) {
        }
    }

    private String substring(long begin, long end, String content)
            throws IOException {
        InputStream input = new ByteArrayInputStream(content.getBytes());
        var t = new SubstringTransformer();
        t.getConfiguration()
            .setBegin(begin)
            .setEnd(end);
        var output = new ByteArrayOutputStream();
        t.accept(TestUtil.newDocContext(
                "N/A", input, output, new Properties(), ParseState.PRE));
        return new String(output.toByteArray());
    }

    @Test
    void testWriteRead() {
        var t = new SubstringTransformer();
        t.getConfiguration()
            .setBegin(1000)
            .setEnd(5000);
        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(t));
    }
}
