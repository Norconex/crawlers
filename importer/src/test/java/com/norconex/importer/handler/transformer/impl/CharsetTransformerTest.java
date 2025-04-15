/* Copyright 2015-2025 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.DocMetaConstants;

class CharsetTransformerTest {

    @Test
    void testCharsetBodyTransformer()
            throws IOException {

        testCharsetBodyTransformer("ISO-8859-1", "ISO-8859-1", true);
        testCharsetBodyTransformer("ISO-8859-2", "ISO-8859-1", false);
        testCharsetBodyTransformer("windows-1250", "ISO-8859-1", true);
        testCharsetBodyTransformer("UTF-8", "ISO-8859-1", true);

        testCharsetBodyTransformer("ISO-8859-1", "ISO-8859-2", true);
        testCharsetBodyTransformer("ISO-8859-2", "ISO-8859-2", false);
        testCharsetBodyTransformer("windows-1250", "ISO-8859-2", true);
        testCharsetBodyTransformer("UTF-8", "ISO-8859-2", true);

        testCharsetBodyTransformer("ISO-8859-1", "windows-1250", true);
        testCharsetBodyTransformer("ISO-8859-2", "windows-1250", true);
        testCharsetBodyTransformer("windows-1250", "windows-1250", false);
        testCharsetBodyTransformer("UTF-8", "windows-1250", true);

        testCharsetBodyTransformer("ISO-8859-1", "UTF-8", true);
        testCharsetBodyTransformer("ISO-8859-2", "UTF-8", true);
        testCharsetBodyTransformer("windows-1250", "UTF-8", true);
        testCharsetBodyTransformer("UTF-8", "UTF-8", true);

        testCharsetBodyTransformer("ISO-8859-1", "KOI8-R", true);
        testCharsetBodyTransformer("ISO-8859-2", "KOI8-R", true);
        testCharsetBodyTransformer("windows-1250", "KOI8-R", true);
        testCharsetBodyTransformer("UTF-8", "KOI8-R", true);
    }

    @Test
    void testCharsetWithGoodSourceBodyTransformer()
            throws IOException {
        var startWith = "En télécommunications".getBytes("UTF-8");

        var t = new CharsetTransformer();
        t.getConfiguration()
                .setSourceCharset(Charset.forName("ISO-8859-1"))
                .setTargetCharset(StandardCharsets.UTF_8);

        var is = getFileStream("/charset/ISO-8859-1.txt");
        var doc = TestUtil.newHandlerContext("ISO-8859-1.txt", is);
        t.handle(doc);
        is.close();

        var targetStartWith = Arrays.copyOf(
                IOUtils.toByteArray(doc.input().asInputStream()),
                startWith.length);
        Assertions.assertArrayEquals(
                startWith, targetStartWith, "ISO-8859-1 > UTF-8");
    }

    @Test
    void testCharsetWithBadSourceBodyTransformer()
            throws IOException {
        var startWith = "En télécommunications".getBytes("UTF-8");

        var t = new CharsetTransformer();
        t.getConfiguration()
                .setSourceCharset(Charset.forName("KOI8-R"))
                .setTargetCharset(null); // using default: UTF-8

        var is = getFileStream("/charset/ISO-8859-1.txt");
        var doc = TestUtil.newHandlerContext("ISO-8859-1.txt", is);
        t.handle(doc);
        var output = IOUtils.toByteArray(doc.input().asInputStream());
        is.close();

        var targetStartWith = Arrays.copyOf(output, startWith.length);
        if (Arrays.equals(startWith, targetStartWith)) {
            Assertions.fail(
                    "Transformation with bad source must not be equal. "
                            + "KOI8-R > UTF-8");
        }
    }

    @Test
    void testBodyError() throws IOException {
        var t = new CharsetTransformer();
        t.getConfiguration()
                .setSourceCharset(null)
                .setTargetCharset(null);
        assertThatExceptionOfType(IOException.class).isThrownBy(
                () -> t.handle(
                        TestUtil.newHandlerContext(
                                "N/A",
                                TestUtil.failingCachedInputStream(),
                                new Properties())));
    }

    @Test
    void testCharsetFieldTransformer()
            throws IOException {

        testCharsetFieldTransformer("ISO-8859-1", "ISO-8859-1");
        testCharsetFieldTransformer("ISO-8859-2", "ISO-8859-1");
        testCharsetFieldTransformer("windows-1250", "ISO-8859-1");
        testCharsetFieldTransformer("UTF-8", "ISO-8859-1");

        testCharsetFieldTransformer("ISO-8859-1", "ISO-8859-2");
        testCharsetFieldTransformer("ISO-8859-2", "ISO-8859-2");
        testCharsetFieldTransformer("windows-1250", "ISO-8859-2");
        testCharsetFieldTransformer("UTF-8", "ISO-8859-2");

        testCharsetFieldTransformer("ISO-8859-1", "windows-1250");
        testCharsetFieldTransformer("ISO-8859-2", "windows-1250");
        testCharsetFieldTransformer("windows-1250", "windows-1250");
        testCharsetFieldTransformer("UTF-8", "windows-1250");

        testCharsetFieldTransformer("ISO-8859-1", "UTF-8");
        testCharsetFieldTransformer("ISO-8859-2", "UTF-8");
        testCharsetFieldTransformer("windows-1250", "UTF-8");
        testCharsetFieldTransformer("UTF-8", "UTF-8");

        testCharsetFieldTransformer("ISO-8859-1", "KOI8-R");
        testCharsetFieldTransformer("ISO-8859-2", "KOI8-R");
        testCharsetFieldTransformer("windows-1250", "KOI8-R");
        testCharsetFieldTransformer("UTF-8", "KOI8-R");
    }

    private void testCharsetFieldTransformer(
            String inCharset, String outCharset)
            throws IOException {

        var fromCharset = Charset.forName(inCharset);
        var toCharset = Charset.forName(outCharset);

        var sourceBytes = "En télécommunications".getBytes(fromCharset);
        var targetBytes = "En télécommunications".getBytes(toCharset);

        var t = new CharsetTransformer();
        t.getConfiguration()
                .setTargetCharset(toCharset)
                .setFieldMatcher(TextMatcher.basic("field1"));

        var metadata = new Properties();
        metadata.set("field1", new String(sourceBytes, fromCharset));
        metadata.set(DocMetaConstants.CONTENT_ENCODING, fromCharset);

        InputStream is = new NullInputStream(0);
        t.handle(
                TestUtil.newHandlerContext(
                        "ref-" + fromCharset + "-" + toCharset, is, metadata));

        var convertedValue = metadata.getString("field1");
        var convertedBytes = convertedValue.getBytes(toCharset);

        var sourceValue = new String(sourceBytes, fromCharset);
        //new String(targetBytes, toCharset);
        System.out.println("=== " + fromCharset + " > " + toCharset + "===");
        System.out.println(" original value: " + sourceValue);
        System.out.println("   target value: " + convertedValue);
        System.out.println("converted value: " + convertedValue);
        System.out.println(" original bytes: " + Arrays.toString(sourceBytes));
        System.out.println("   target bytes: " + Arrays.toString(targetBytes));
        System.out
                .println("converted bytes: " + Arrays.toString(convertedBytes));

        Assertions.assertArrayEquals(
                targetBytes, convertedBytes, fromCharset + " > " + toCharset);
    }

    private void testCharsetBodyTransformer(
            String inCharset, String outCharset, boolean detect)
            throws IOException {

        var fromCharset = Charset.forName(inCharset);
        var toCharset = Charset.forName(outCharset);

        var startWith = "En télécommunications".getBytes(toCharset);

        var t = new CharsetTransformer();
        if (!detect) {
            t.getConfiguration().setSourceCharset(fromCharset);
        }
        t.getConfiguration().setTargetCharset(toCharset);

        var is = getFileStream("/charset/" + fromCharset + ".txt");
        var doc = TestUtil.newHandlerContext(fromCharset + ".txt", is);
        t.handle(doc);

        var output = IOUtils.toByteArray(doc.input().asInputStream());

        is.close();

        var targetStartWith = Arrays.copyOf(output, startWith.length);

        //        System.out.println("=== " + fromCharset + " > " + toCharset + "===");
        //        System.out.println(Arrays.toString(startWith));
        //        System.out.println(Arrays.toString(targetStartWith));

        Assertions.assertArrayEquals(
                startWith, targetStartWith, fromCharset + " > " + toCharset);
    }

    private InputStream getFileStream(String resourcePath) {
        return getClass().getResourceAsStream(resourcePath);
    }

    @Test
    void testWriteRead() {
        var t = new CharsetTransformer();
        t.getConfiguration()
                .setTargetCharset(StandardCharsets.ISO_8859_1)
                .setFieldMatcher(TextMatcher.regex(".*"));
        BeanMapper.DEFAULT.assertWriteRead(t);
    }
}
