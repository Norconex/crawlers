/* Copyright 2015-2022 Norconex Inc.
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
package com.norconex.importer.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.utils.CharsetUtils;

import com.norconex.commons.lang.io.ByteArrayOutputStream;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocRecord;
import com.norconex.importer.parser.ParseState;

import lombok.extern.slf4j.Slf4j;

/**
 * Character set utility methods.
 */
@Slf4j
public final class CharsetUtil {

    private CharsetUtil() {}

    /**
     * Converts the character encoding of the supplied input value.
     * @param input input value to apply conversion
     * @param inputCharset character set of the input value
     * @param outputCharset desired character set of the output value
     * @return the converted value
     * @throws IOException problem converting character set
     */
    public static String convertCharset(
            String input, String inputCharset,
            String outputCharset) throws IOException {
        try (var is =
                new ByteArrayInputStream(input.getBytes(inputCharset));
                var os = new ByteArrayOutputStream()) {
            convertCharset(is, inputCharset, os, outputCharset);
            os.flush();
            return os.toString(outputCharset);
        }
    }

    /**
     * Converts the character encoding of the supplied input.
     * @param input input stream to apply conversion
     * @param inputCharset character set of the input stream
     * @param output where converted stream will be stored
     * @param outputCharset desired character set of the output stream
     * @throws IOException problem converting character set
     */
    public static void convertCharset(
            InputStream input, String inputCharset,
            OutputStream output, String outputCharset) throws IOException {
        var decoder = Charset.forName(inputCharset).newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        var encoder = Charset.forName(outputCharset).newEncoder();
        encoder.onMalformedInput(CodingErrorAction.REPLACE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        Reader reader = new InputStreamReader(input, decoder);
        Writer writer = new OutputStreamWriter(output, encoder);
        IOUtils.copyLarge(reader, writer);
        writer.flush();
        rewind(input);
    }

    /**
     * Detects the character encoding of a string.
     * @param input the input to detect encoding on
     * @return the character encoding official name or <code>null</code>
     *         if the input is null or blank
     */
    public static String detectCharset(String input) {
        return detectCharset(input, null);
    }

    /**
     * Detects the character encoding of a string. If the string has
     * a declared character encoding, specifying
     * it will influence the detection result.
     * @param input the input to detect encoding on
     * @param declaredEncoding declared input encoding, if known
     * @return the character encoding official name or <code>null</code>
     *         if the input is null or blank
     */
    public static String detectCharset(
            String input, String declaredEncoding) {
        if (StringUtils.isBlank(input)) {
            return null;
        }
        var cd = new CharsetDetector();
        if (StringUtils.isNotBlank(declaredEncoding)) {
            cd.setDeclaredEncoding(declaredEncoding);
        }
        cd.enableInputFilter(true);
        cd.setText(input.getBytes(StandardCharsets.UTF_8));
        return doDetect(cd);
    }

    /**
     * Detects the character encoding of an input stream.
     * {@link InputStream#markSupported()} must return <code>true</code>
     * otherwise no decoding will be attempted.
     * @param input the input to detect encoding on
     * @return the character encoding official name or <code>null</code>
     *         if input is null
     * @throws IOException if there is a problem find the character encoding
     */
    public static String detectCharset(InputStream input) throws IOException {
        return detectCharset(input, null);
    }

    /**
     * Detects the character encoding of an input stream.  If the string has
     * a declared character encoding, specifying
     * it will influence the detection result.
     * {@link InputStream#markSupported()} must return <code>true</code>
     * otherwise no decoding will be attempted.
     * @param input the input to detect encoding on
     * @param declaredEncoding declared input encoding, if known
     * @return the character encoding official name or <code>null</code>
     *         if input is null
     * @throws IOException if there is a problem find the character encoding
     */
    public static String detectCharset(
            InputStream input, String declaredEncoding) throws IOException {
        if (input == null) {
            return null;
        }
        if (!input.markSupported()) {
            LOG.warn("mark/reset not supported on input stream. "
                    + "Will not attempt to detect encoding.");
            return declaredEncoding;
        }

        var cd = new CharsetDetector();
        if (StringUtils.isNotBlank(declaredEncoding)) {
            cd.setDeclaredEncoding(declaredEncoding);
        }
        cd.enableInputFilter(true);
        cd.setText(input);
        rewind(input);
        return doDetect(cd);
    }

    /**
     * Detects a document character encoding. It first checks if it is defined
     * in the document {@link DocRecord#getContentEncoding()}. If not,
     * it will attempt to detect it from the document input stream.
     * This method will NOT set the detected encoding on the {@link DocRecord}.
     * If unable to detect, <code>UTF-8</code> is assumed.
     * @param doc document to detect encoding on
     * @return string representation of character encoding
     * @throws IOException problem detecting charset
     */
    public static String detectCharset(Doc doc) throws IOException {
        return detectCharsetIfBlank(null, doc);
    }
    /**
     * Detects a document character encoding if the supplied
     * <code>charset</code> is blank. When blank, it checks if it is defined
     * in the document {@link DocRecord#getContentEncoding()}. If not,
     * it will attempt to detect it from the document input stream.
     * This method will NOT set the detected encoding on the {@link DocRecord}.
     * If unable to detect, <code>UTF-8</code> is assumed.
     * @param charset character encoding to use if not blank
     * @param doc document to detect encoding on
     * @return supplied charset if not blank, or the detected charset
     * @throws IOException problem detecting charset
     */
    public static String detectCharsetIfBlank(String charset, Doc doc)
            throws IOException {
        if (StringUtils.isNotBlank(charset)) {
            return charset;
        }
        var detectedCharset = doc.getDocRecord().getContentEncoding();
        if (StringUtils.isBlank(detectedCharset)) {
            detectedCharset = detectCharset(doc.getInputStream());
        }
        if (StringUtils.isBlank(detectedCharset)) {
            detectedCharset = StandardCharsets.UTF_8.toString();
        }
        return CharsetUtils.clean(detectedCharset);
    }

    /**
     * Detects a document character encoding if the supplied
     * <code>charset</code> is blank. When blank,
     * it will attempt to detect it from the input stream.
     * If unable to detect, <code>UTF-8</code> is assumed.
     * @param charset character encoding to use if not blank
     * @param is input stream
     * @return supplied charset if not blank, or the detected charset
     * @throws IOException problem detecting charset
     */
    public static String detectCharsetIfBlank(String charset, InputStream is)
            throws IOException {
        if (StringUtils.isNotBlank(charset)) {
            return charset;
        }

        var detectedCharset = detectCharset(is);
        if (StringUtils.isBlank(detectedCharset)) {
            detectedCharset = StandardCharsets.UTF_8.toString();
        }
        return CharsetUtils.clean(detectedCharset);
    }

    /**
     * Returns the first non-blank character encoding, or returns UTF-8 if they
     * are all blank.
     * @param charsets character encodings to test
     * @return first non-blank, or UTF-8
     */
    public static String firstNonBlankOrUTF8(String... charsets) {
        var encoding = StringUtils.firstNonBlank(charsets);
        if (StringUtils.isBlank(encoding)) {
            encoding = StandardCharsets.UTF_8.toString();
        }
        return encoding;
    }
    /**
     * Returns the first non-blank character encoding, or returns UTF-8 if they
     * are all blank or in post-parse state.  That is, UTF-8 is always
     * returned if parsing has already occurred (since parsing converts
     * content encoding to UTF-8).
     * @param parseState document parsing state
     * @param charsets character encodings to test
     * @return first non-blank, or UTF-8
     */
    public static String firstNonBlankOrUTF8(
            ParseState parseState, String... charsets) {
        if (ParseState.isPost(parseState)) {
            return StandardCharsets.UTF_8.toString();
        }
        return firstNonBlankOrUTF8(charsets);
    }

    /*
     * We bias towards UTF-8: If UTF-8 is detected but not the highest, and
     * the highest is below 50% confidence, we check if there is less than
     * 20% difference in confidence between the highest and UTF-8 charset
     * and if so, we return UTF-8 instead.
     */
    private static String doDetect(CharsetDetector cd) {
        var matches = cd.detectAll();
        if (ArrayUtils.isEmpty(matches)) {
            return null;
        }

        String charset = null;

        var utf8 = StandardCharsets.UTF_8.toString();

        var firstMatch = matches[0];
        if (matches.length == 1
                || utf8.equalsIgnoreCase(firstMatch.getName())) {
            charset = firstMatch.getName();
        }

        if (charset == null || firstMatch.getConfidence() <= 50) {
            var utf8Match = Stream.of(matches)
                    .filter(m -> utf8.equalsIgnoreCase(m.getName()))
                    .findFirst();
            if (utf8Match.isPresent()
                    && firstMatch.getConfidence()
                            - utf8Match.get().getConfidence() <= 20) {
                charset = utf8;
            }
        }

        if (charset == null) {
            charset = firstMatch.getName();
        }

        LOG.debug("Detected encoding: {}", charset);
        return charset;
    }

    private static void rewind(InputStream is) {
        //MAYBE: investigate why regular reset on CachedInputStream has
        //no effect and returns an empty stream when read again. Fix that
        //instead of having this method.
        if (is instanceof CachedInputStream cis) {
            cis.rewind();
        }
    }
}
