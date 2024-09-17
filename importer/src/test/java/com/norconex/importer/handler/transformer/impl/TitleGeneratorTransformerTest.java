/* Copyright 2015-2024 Norconex Inc.
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
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.parser.ParseState;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class TitleGeneratorTransformerTest {

    // Test for: https://github.com/Norconex/importer/issues/74
    @Test
    void testNullFromField() throws IOException {

        var t = new TitleGeneratorTransformer();
        t.getConfiguration()
                .setFromField("nullField")
                .setDetectHeading(true);

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/plain");
        TestUtil.transform(t, "test.txt", metadata, ParseState.POST);

        Assertions.assertNull(
                metadata.getString(DocMetadata.GENERATED_TITLE),
                "Title should be null");
    }

    @Test
    void testSummarizeTitle() throws IOException {

        var t = new TitleGeneratorTransformer();
        t.getConfiguration()
                .setToField("mytitle");

        var file = TestUtil.getAliceTextFile();
        InputStream is = new BufferedInputStream(new FileInputStream(file));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/plain");
        t.accept(
                TestUtil.newHandlerContext(
                        file.getAbsolutePath(), is, metadata, ParseState.POST));
        is.close();

        var title = metadata.getString("mytitle");

        LOG.debug("TITLE IS: " + title);
        Assertions.assertEquals(
                "that Alice had begun to think that very few things "
                        + "indeed were really impossible.",
                title,
                "Wrong title.");
    }

    @Test
    void testHeadingTitle() throws IOException {
        var t = new TitleGeneratorTransformer();
        t.getConfiguration()
                .setDetectHeading(true)
                .setDetectHeadingMinLength(5);

        var file = TestUtil.getAliceTextFile();
        InputStream is = new BufferedInputStream(new FileInputStream(file));

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/plain");
        t.accept(
                TestUtil.newHandlerContext(
                        file.getAbsolutePath(), is, metadata, ParseState.POST));
        is.close();

        var title = metadata.getString(DocMetadata.GENERATED_TITLE);

        LOG.debug("TITLE IS: " + title);
        Assertions.assertEquals("Chapter I", title, "Wrong title.");
    }

    @Test
    void testFallbackTitle() throws IOException {
        var t = new TitleGeneratorTransformer();

        InputStream is = new ByteArrayInputStream(
                "This is the first line. This is another line.".getBytes());

        var metadata = new Properties();
        metadata.set(DocMetadata.CONTENT_TYPE, "text/plain");

        t.accept(
                TestUtil.newHandlerContext(
                        "test.txt", is, metadata, ParseState.POST));
        is.close();

        var title = metadata.getString(DocMetadata.GENERATED_TITLE);

        LOG.debug("TITLE IS: {}", title);
        Assertions.assertEquals(
                "This is the first line.", title, "Wrong title.");
    }

    @Test
    void testWriteRead() {
        var t = new TitleGeneratorTransformer();
        t.getConfiguration()
                .setFromField("potato")
                .setToField("banana")
                .setOnSet(PropertySetter.APPEND)
                .setTitleMaxLength(300)
                .setDetectHeading(true)
                .setDetectHeadingMaxLength(200)
                .setDetectHeadingMinLength(20);
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(t));
    }
}
