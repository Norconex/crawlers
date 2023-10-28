/* Copyright 2010-2023 Norconex Inc.
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

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import java.io.IOException;
import com.norconex.importer.handler.parser.ParseState;

class StripAfterTransformerTest {

    @Test
    void testTransformTextDocument()
            throws IOException, IOException {
        var t = new StripAfterTransformer();
        t.getConfiguration()
            .setStripAfterMatcher(TextMatcher.regex("<p>").setIgnoreCase(true))
            .setInclusive(true);
        var htmlFile = TestUtil.getAliceHtmlFile();
        InputStream is = new BufferedInputStream(new FileInputStream(htmlFile));

        var os = new ByteArrayOutputStream();
        var metadata = new Properties();

        metadata.set(DocMetadata.CONTENT_TYPE, "text/html");
        t.accept(TestUtil.newDocContext(htmlFile.getAbsolutePath(),
                is, os, metadata, ParseState.PRE));

        Assertions.assertEquals(
                539, TestUtil.toUtf8UnixLineString(os).length(),
                "Length of doc content after transformation is incorrect.");

        is.close();
        os.close();
    }


    @Test
    void testWriteRead() {
        var t = new StripAfterTransformer();
        t.getConfiguration()
            .setInclusive(true)
            .setStripAfterMatcher(TextMatcher.regex("<p>").setIgnoreCase(true));

        assertThatNoException().isThrownBy(() ->
                BeanMapper.DEFAULT.assertWriteRead(t));
    }
}
