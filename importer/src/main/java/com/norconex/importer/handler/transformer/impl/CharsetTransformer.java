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
package com.norconex.importer.handler.transformer.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.utils.CharsetUtils;

import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.impl.CharsetTagger;
import com.norconex.importer.handler.transformer.AbstractDocumentTransformer;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;
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
@EqualsAndHashCode
@ToString
@Slf4j
public class CharsetTransformer extends AbstractDocumentTransformer
        implements XMLConfigurable {

    public static final String DEFAULT_TARGET_CHARSET =
            StandardCharsets.UTF_8.toString();

    private String targetCharset = DEFAULT_TARGET_CHARSET;
    private String sourceCharset = null;

    @Override
    protected void transformApplicableDocument(
            HandlerDoc doc, final InputStream input, final OutputStream output,
            final ParseState parseState) throws ImporterHandlerException {

        var inputCharset = detectCharsetIfBlank(input);

        //--- Get target charset ---
        var outputCharset = targetCharset;
        if (StringUtils.isBlank(outputCharset)) {
            outputCharset = StandardCharsets.UTF_8.toString();
        }
        outputCharset = CharsetUtils.clean(outputCharset);

        // Do not proceed if encoding is already what we want
        if (inputCharset.equals(outputCharset)) {
            LOG.debug("Source and target encodings are the same for {}",
                    doc.getReference());
            return;
        }

        //--- Convert ---
        try {
            CharsetUtil.convertCharset(
                    input, inputCharset, output, outputCharset);
        } catch (IOException e) {
            LOG.warn("Cannot convert character encoding from {} to {}. "
                    + "Encoding will remain unchanged. Reference: {}",
                    inputCharset, outputCharset, doc.getReference(), e);
        }
    }

    public String getTargetCharset() {
        return targetCharset;
    }
    public void setTargetCharset(final String targetCharset) {
        this.targetCharset = targetCharset;
    }

    public String getSourceCharset() {
        return sourceCharset;
    }
    public void setSourceCharset(final String sourceCharset) {
        this.sourceCharset = sourceCharset;
    }

    private String detectCharsetIfBlank(InputStream input)
            throws ImporterHandlerException {
        try {
            return CharsetUtil.detectCharsetIfBlank(sourceCharset, input);
        } catch (IOException e) {
            throw new ImporterHandlerException("Could not detect content "
                    + "character encoding.", e);
        }
    }

    @Override
    protected void loadHandlerFromXML(final XML xml) {
        setSourceCharset(xml.getString("@sourceCharset", sourceCharset));
        setTargetCharset(xml.getString("@targetCharset", targetCharset));
    }

    @Override
    protected void saveHandlerToXML(final XML xml) {
        xml.setAttribute("sourceCharset", sourceCharset);
        xml.setAttribute("targetCharset", targetCharset);
    }
}
