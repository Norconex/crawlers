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
package com.norconex.importer.charset;

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

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.io.ByteArrayOutputStream;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.importer.handler.parser.ParseState;

import lombok.NonNull;

/**
 * Character set utility methods.
 */
public final class CharsetUtil {

    private CharsetUtil() {
    }

    /**
     * Converts the character encoding of the supplied input value.
     * @param input input value to apply conversion
     * @param inputCharset character set of the input value
     * @param outputCharset desired character set of the output value
     * @return the converted value
     * @throws IOException problem converting character set
     */
    public static String convertCharset(
            String input,
            @NonNull Charset inputCharset,
            @NonNull Charset outputCharset
    ) throws IOException {
        return convertCharset(
                input, inputCharset.toString(), outputCharset.toString()
        );
    }

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
            String outputCharset
    ) throws IOException {
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
            @NonNull InputStream input, @NonNull Charset inputCharset,
            @NonNull OutputStream output, @NonNull Charset outputCharset
    )
            throws IOException {
        convertCharset(
                input, inputCharset.toString(),
                output, outputCharset.toString()
        );
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
            OutputStream output, String outputCharset
    ) throws IOException {
        var decoder = Charset.forName(inputCharset).newDecoder();
        decoder.onMalformedInput(CodingErrorAction.REPLACE);
        decoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        var encoder = Charset.forName(outputCharset).newEncoder();
        encoder.onMalformedInput(CodingErrorAction.REPLACE);
        encoder.onUnmappableCharacter(CodingErrorAction.REPLACE);
        try (Reader reader = new InputStreamReader(input, decoder);
                Writer writer = new OutputStreamWriter(output, encoder)) {
            IOUtils.copyLarge(reader, writer);
            writer.flush();
        }
        rewind(input);
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
            ParseState parseState, String... charsets
    ) {
        if (ParseState.isPost(parseState)) {
            return StandardCharsets.UTF_8.toString();
        }
        return firstNonBlankOrUTF8(charsets);
    }

    /**
     * Returns the first non-blank character encoding, or returns UTF-8 if they
     * are all blank.
     * @param charsets character encodings to test
     * @return first non-blank, or UTF-8
     */
    public static Charset firstNonNullOrUTF8(Charset... charsets) {
        var charset = ObjectUtils.firstNonNull(charsets);
        if (charset == null) {
            charset = StandardCharsets.UTF_8;
        }
        return charset;
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
    public static Charset firstNonNullOrUTF8(
            ParseState parseState, Charset... charsets
    ) {
        if (ParseState.isPost(parseState)) {
            return StandardCharsets.UTF_8;
        }
        return firstNonNullOrUTF8(charsets);
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
