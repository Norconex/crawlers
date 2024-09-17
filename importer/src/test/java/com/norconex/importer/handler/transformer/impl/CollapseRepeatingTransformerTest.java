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

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.parser.ParseState;

class CollapseRepeatingTransformerTest {

    private final String xml = """
            <handler>
              <ignoreCase>true</ignoreCase>
              <strings>
                <string>\\stext</string>
                <string>\\t</string>
                <string>\\n\\r</string>
                <string>\\s</string>
                <string>.</string>
              </strings>
            </handler>""";

    @Test
    void testTransformTextDocument() throws IOException {
        var text = "\t\tThis is the text TeXt I want to modify...\n\r\n\r"
                + "     Too much space.";

        var t = new CollapseRepeatingTransformer();

        try (Reader reader = new InputStreamReader(
                IOUtils.toInputStream(xml, StandardCharsets.UTF_8))) {
            BeanMapper.DEFAULT.read(t, reader, Format.XML);
        }

        try (var is = IOUtils.toInputStream(
                text, StandardCharsets.UTF_8)) {
            var doc = TestUtil.newHandlerContext(
                    "dummyRef", is, new Properties(), ParseState.POST);
            t.accept(doc);
            var response = IOUtils.toString(doc.input().asReader());
            Assertions.assertEquals(
                    "\tthis is the text i want to modify.\n\r too much space.",
                    response.toLowerCase());
        }
    }

    @Test
    void testWriteRead() throws IOException {
        var t = new CollapseRepeatingTransformer();
        try (Reader reader = new InputStreamReader(
                IOUtils.toInputStream(xml, StandardCharsets.UTF_8))) {
            BeanMapper.DEFAULT.read(t, reader, Format.XML);
        }
        assertThatNoException()
                .isThrownBy(() -> BeanMapper.DEFAULT.assertWriteRead(t));
    }
}
