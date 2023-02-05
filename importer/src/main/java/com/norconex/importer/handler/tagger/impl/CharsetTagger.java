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
package com.norconex.importer.handler.tagger.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.utils.CharsetUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.handler.transformer.impl.CharsetTransformer;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.util.CharsetUtil;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Converts one or more field values (if needed) from a source character
 * encoding (charset) to a target one. Both the source and target character
 * encodings are optional. If no source character encoding is explicitly
 * provided, it first tries to detect the encoding of the field values
 * before converting them to the target encoding. If the source
 * character encoding cannot be established, the content encoding will remain
 * unchanged. When no target character encoding is specified, UTF-8 is assumed.
 * </p>
 *
 * <h3>Should I use this tagger?</h3>
 * <p>
 * Before using this tagger, you need to know the parsing of documents
 * by the importer (using the default document parser factory) will try to
 * convert and return fields as UTF-8 (for most, if not all content-types).
 * If UTF-8 is your desired target, it only make sense to use this tagger
 * as a pre-parsing handler (for text content-types only) when it is important
 * to work with a specific character encoding before parsing.
 * If on the other hand you wish to convert to a character encoding to a
 * target different than UTF-8, you can use this tagger as a post-parsing
 * handler to do so.
 * </p>
 *
 * <h3>Conversion is not flawless</h3>
 * <p>
 * Because character encoding detection is not always accurate and because
 * documents sometime mix different encoding, there is no guarantee this
 * class will handle ALL character encoding conversions properly.
 * </p>
 * {@nx.xml.usage
 *  <handler class="com.norconex.importer.handler.tagger.impl.CharsetTagger"
 *          sourceCharset="(character encoding)"
 *          targetCharset="(character encoding)">
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (expression matching fields to be converted)
 *   </fieldMatcher>
 *
 *  </handler>
 * }
 *
 * {@nx.xml.example
 *  <handler class="CharsetTagger"
 *          sourceCharset="ISO-8859-1" targetCharset="UTF-8">
 *    <fieldMatcher>description</fieldMatcher>
 *  </handler>
 * }
 * <p>
 * The above example converts the characters of a "description" field from
 * "ISO-8859-1" to "UTF-8".
 * </p>
 *
 * @see CharsetTransformer
 */
@SuppressWarnings("javadoc")
@Slf4j
@EqualsAndHashCode
@ToString
public class CharsetTagger extends AbstractDocumentTagger
        implements XMLConfigurable {

    public static final String DEFAULT_TARGET_CHARSET =
            StandardCharsets.UTF_8.toString();

    private String targetCharset = DEFAULT_TARGET_CHARSET;
    private String sourceCharset = null;
    private final TextMatcher fieldMatcher = new TextMatcher();

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {

        if (fieldMatcher.getPattern() == null) {
            throw new ImporterHandlerException(
                    "\"fieldMatcher\" cannot be blank on CharsetTagger.");
        }

        for (Entry<String, List<String>> en :
            doc.getMetadata().matchKeys(fieldMatcher).entrySet()) {
            LOG.debug("Field to convert charset: {}", en.getKey());
            convertCharset(doc.getReference(), doc.getMetadata(), en.getKey());
        }
    }

    /**
     * Gets field matcher.
     * @return field matcher
         */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Set field matcher (copy).
     * @param fieldMatcher field matcher
         */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    private void convertCharset(
            String reference, Properties metadata, String metaField) {
        var values = metadata.get(metaField);
        if (values == null) {
            return;
        }
        var declaredEncoding =
                metadata.getString(DocMetadata.CONTENT_ENCODING);
        List<String> newValues = new ArrayList<>();
        for (String value : values) {
            var newValue =
                    convertCharset(reference, value, declaredEncoding);
            newValues.add(newValue);
        }
        metadata.setList(metaField, newValues);
    }

    private String convertCharset(
            String reference, String value, String declaredEncoding) {

        //--- Get source charset ---
        var inputCharset = sourceCharset;
        if (StringUtils.isBlank(inputCharset)) {
            inputCharset = CharsetUtil.detectCharset(value, declaredEncoding);
        }
        // Do not attempt conversion of no source charset is found
        if (StringUtils.isBlank(inputCharset)) {
            return value;
        }
        inputCharset = CharsetUtils.clean(inputCharset);

        //--- Get target charset ---
        var outputCharset = targetCharset;
        if (StringUtils.isBlank(outputCharset)) {
            outputCharset = StandardCharsets.UTF_8.toString();
        }
        outputCharset = CharsetUtils.clean(outputCharset);

        // Do not proceed if encoding is already what we want
        if (inputCharset.equals(outputCharset)) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Source and target encodings are the same for "
                        + reference);
            }
            return value;
        }

        //--- Convert ---
        try {
            value = CharsetUtil.convertCharset(
                    value, inputCharset, outputCharset);
        } catch (IOException e) {
            LOG.warn("Cannot convert character encoding from " + inputCharset
                    + " to " + outputCharset
                    + ". Encoding will remain unchanged. "
                    + "Reference: " + reference, e);
        }
        return value;
    }

    public String getTargetCharset() {
        return targetCharset;
    }
    public void setTargetCharset(String targetCharset) {
        this.targetCharset = targetCharset;
    }

    public String getSourceCharset() {
        return sourceCharset;
    }
    public void setSourceCharset(String sourceCharset) {
        this.sourceCharset = sourceCharset;
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setSourceCharset(xml.getString("@sourceCharset", sourceCharset));
        setTargetCharset(xml.getString("@targetCharset", targetCharset));
        fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("sourceCharset", sourceCharset);
        xml.setAttribute("targetCharset", targetCharset);
        fieldMatcher.saveToXML(xml.addElement("fieldMatcher"));
    }
}
