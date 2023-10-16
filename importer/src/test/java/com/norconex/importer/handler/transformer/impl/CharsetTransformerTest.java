/* Copyright 2015-2023 Norconex Inc.
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

import static java.io.OutputStream.nullOutputStream;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;

class CharsetTransformerTest {

    @Test
    void testCharsetTransformer()
            throws ImporterHandlerException, IOException {

        testCharsetTransformer("ISO-8859-1",   "ISO-8859-1", true);
        testCharsetTransformer("ISO-8859-2",   "ISO-8859-1", false);
        testCharsetTransformer("windows-1250", "ISO-8859-1", true);
        testCharsetTransformer("UTF-8",        "ISO-8859-1", true);

        testCharsetTransformer("ISO-8859-1",   "ISO-8859-2", true);
        testCharsetTransformer("ISO-8859-2",   "ISO-8859-2", false);
        testCharsetTransformer("windows-1250", "ISO-8859-2", true);
        testCharsetTransformer("UTF-8",        "ISO-8859-2", true);

        testCharsetTransformer("ISO-8859-1",   "windows-1250", true);
        testCharsetTransformer("ISO-8859-2",   "windows-1250", true);
        testCharsetTransformer("windows-1250", "windows-1250", false);
        testCharsetTransformer("UTF-8",        "windows-1250", true);

        testCharsetTransformer("ISO-8859-1",   "UTF-8", true);
        testCharsetTransformer("ISO-8859-2",   "UTF-8", true);
        testCharsetTransformer("windows-1250", "UTF-8", true);
        testCharsetTransformer("UTF-8",        "UTF-8", true);

        testCharsetTransformer("ISO-8859-1",   "KOI8-R", true);
        testCharsetTransformer("ISO-8859-2",   "KOI8-R", true);
        testCharsetTransformer("windows-1250", "KOI8-R", true);
        testCharsetTransformer("UTF-8",        "KOI8-R", true);
    }


    @Test
    void testCharsetWithGoodSourceTransformer()
            throws ImporterHandlerException, IOException {
        var startWith = "En télécommunications".getBytes("UTF-8");

        var t = new CharsetTransformer();
        t.getConfiguration()
            .setSourceCharset(Charset.forName("ISO-8859-1"))
            .setTargetCharset(StandardCharsets.UTF_8);

        var os = new ByteArrayOutputStream();
        var metadata = new Properties();
        var is = getFileStream("/charset/ISO-8859-1.txt");

        t.accept(TestUtil.newDocContext("ISO-8859-1.txt", is, os, metadata));

        var output = os.toByteArray();

        is.close();
        os.close();

        var targetStartWith = Arrays.copyOf(output, startWith.length);
        Assertions.assertArrayEquals(
                startWith, targetStartWith, "ISO-8859-1 > UTF-8");
    }

    @Test
    void testCharsetWithBadSourceTransformer()
            throws ImporterHandlerException, IOException {
        var startWith = "En télécommunications".getBytes("UTF-8");

        var t = new CharsetTransformer();
        t.getConfiguration()
            .setSourceCharset(Charset.forName("KOI8-R"))
            .setTargetCharset(null);  // using default: UTF-8

        var os = new ByteArrayOutputStream();
        var metadata = new Properties();
        var is = getFileStream("/charset/ISO-8859-1.txt");

        t.accept(TestUtil.newDocContext("ISO-8859-1.txt", is, os, metadata));

        var output = os.toByteArray();

        is.close();
        os.close();

        var targetStartWith = Arrays.copyOf(output, startWith.length);
        if (Arrays.equals(startWith, targetStartWith)) {
            Assertions.fail("Transformation with bad source must not be equal. "
                    + "KOI8-R > UTF-8");
        }
    }

    @Test
    void testError()
            throws ImporterHandlerException, IOException {
        var t = new CharsetTransformer();
        t.getConfiguration()
            .setSourceCharset(null)
            .setTargetCharset(null);
        assertThatExceptionOfType(ImporterHandlerException.class).isThrownBy(
                () -> t.accept(TestUtil.newDocContext(
                        "N/A", TestUtil.failingCachedInputStream(),
                        nullOutputStream(), new Properties())));
    }

    private void testCharsetTransformer(
            String inCharset, String outCharset, boolean detect)
            throws ImporterHandlerException, IOException {

        var fromCharset = Charset.forName(inCharset);
        var toCharset = Charset.forName(outCharset);

        var startWith = "En télécommunications".getBytes(toCharset);
        var blankBytes = new byte[startWith.length];

        var t = new CharsetTransformer();
        if (!detect) {
            t.getConfiguration().setSourceCharset(fromCharset);
        }
        t.getConfiguration().setTargetCharset(toCharset);

        var os = new ByteArrayOutputStream();
        var metadata = new Properties();
        var is = getFileStream("/charset/" + fromCharset + ".txt");

        t.accept(
                TestUtil.newDocContext(fromCharset + ".txt", is, os, metadata));

        var output = os.toByteArray();

        is.close();
        os.close();

        var targetStartWith = Arrays.copyOf(output, startWith.length);

//        System.out.println("=== " + fromCharset + " > " + toCharset + "===");
//        System.out.println(Arrays.toString(startWith));
//        System.out.println(Arrays.toString(targetStartWith));

        if (fromCharset.equals(toCharset)) {
            Assertions.assertArrayEquals(blankBytes, targetStartWith,
                    fromCharset + " > " + toCharset);
        } else {
            Assertions.assertArrayEquals(startWith, targetStartWith,
                    fromCharset + " > " + toCharset);
        }
    }

    private InputStream getFileStream(String resourcePath) {
        return getClass().getResourceAsStream(resourcePath);
    }


    @Test
    void testWriteRead() {
        var t = new CharsetTransformer();
        t.getConfiguration().setTargetCharset(StandardCharsets.ISO_8859_1);
        BeanMapper.DEFAULT.assertWriteRead(t);
    }
}
