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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.importer.charset.CharsetDetector;
import com.norconex.importer.charset.CharsetUtil;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.DocumentTransformer;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Transforms a document content (if needed) from a source character
 * encoding (charset) to a target one. Both the source and target character
 * encodings are optional. If no source character encoding is explicitly
 * provided, it first tries to detect the encoding of the document
 * content before converting it to the target encoding. If the source
 * character encoding cannot be established, the content encoding will remain
 * unchanged. When no target character encoding is specified, UTF-8 is assumed.
 * </p>
 *
 * <h3>Should I use this transformer?</h3>
 * <p>
 * Before using this transformer, you need to know the parsing of documents
 * by the importer using default document parser factory will try to convert
 * and return content as UTF-8 (for most, if not all content-types).
 * If UTF-8 is your desired target, it only make sense to use this transformer
 * as a pre-parsing handler (for text content-types only) when it is important
 * to work with a specific character encoding before parsing.
 * If on the other hand you wish to convert to a character encoding to a
 * target different than UTF-8, you can use this transformer as a post-parsing
 * handler to do so.
 * </p>
 *
 * <h3>Conversion is not flawless</h3>
 * <p>
 * Because character encoding detection is not always accurate and because
 * documents sometime mix different encoding, there is no guarantee this
 * class will handle ALL character encoding conversions properly.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.CharsetTransformer"
 *     sourceCharset="(character encoding)"
 *     targetCharset="(character encoding)">
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </handler>
 * }
 *
 *
 * {@nx.xml.example
 * <handler class="CharsetTransformer"
 *     sourceCharset="ISO-8859-1" targetCharset="UTF-8">
 * </handler>
 * }
 * <p>
 * The above example converts the content of a document from "ISO-8859-1"
 * to "UTF-8".
 * </p>
 *
 * @see CharsetTagger
 */
@SuppressWarnings("javadoc")
@Data
@Slf4j
public class CharsetTransformer
        implements DocumentTransformer, Configurable<CharsetTransformerConfig> {

    private final CharsetTransformerConfig configuration =
            new CharsetTransformerConfig();

    @Override
    public void accept(DocContext docCtx) throws ImporterHandlerException {
        if (configuration.getFieldMatcher().isSet()) {
            // Fields
            for (Entry<String, List<String>> en : docCtx.metadata().matchKeys(
                    configuration.getFieldMatcher()).entrySet()) {
                var key = en.getKey();
                List<String> newVals = new ArrayList<>();
                for (String val : en.getValue()) {
                    var out = new ByteArrayOutputStream();
                    var charset = doTransform(
                            docCtx,
                            new ByteArrayInputStream(val.getBytes()),
                            out);
                    newVals.add(new String(out.toByteArray(), charset));
                }
                docCtx.metadata().replace(key, newVals);
            }
        } else {
            // Body
            doTransform(
                    docCtx,
                    docCtx.readContent().asInputStream(),
                    docCtx.writeContent().toOutputStream());
        }
    }

    // returns final charset
    private Charset doTransform(
            DocContext docCtx, InputStream in, OutputStream out)
                    throws ImporterHandlerException {

        var inputCharset = detectCharsetIfNull(
                docCtx.readContent().asInputStream());

        //--- Get target charset ---
        var outputCharset =  configuration.getTargetCharset();
        if (outputCharset == null) {
            outputCharset = StandardCharsets.UTF_8;
        }

        // Do not proceed if encoding is already what we want
        if (inputCharset.equals(outputCharset)) {
            LOG.debug("Source and target encodings are the same for {}",
                    docCtx.reference());
            return outputCharset;
        }

        //--- Convert ---
        try {
            CharsetUtil.convertCharset(
                    in, inputCharset,
                    out, outputCharset);
        } catch (IOException e) {
            LOG.warn("Cannot convert character encoding from {} to {}. "
                    + "Encoding will remain unchanged. Reference: {}",
                    inputCharset, outputCharset, docCtx.reference(), e);
        }
        return outputCharset;
    }

    private Charset detectCharsetIfNull(InputStream input)
            throws ImporterHandlerException {
        try {
            return CharsetDetector.builder()
                .priorityCharset(() -> configuration.getSourceCharset())
                .build()
                .detect(input);
        } catch (IOException e) {
            throw new ImporterHandlerException("Could not detect content "
                    + "character encoding.", e);
        }
    }
}
