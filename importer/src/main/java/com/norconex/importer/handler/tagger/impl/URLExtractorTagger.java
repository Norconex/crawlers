/* Copyright 2020 Norconex Inc.
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
import java.io.Reader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.Regex;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractCharStreamTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Extracts unique URLs matching specific patterns in plain text content and
 * store them in a given field.
 * </p>
 * <p>
 * URL-matching patterns used are relatively simple. It looks for strings
 * starting with <code>http://</code>, <code>https://</code>,
 * or <code>www.</code>.  The later is prefixed with <code>https://</code>
 * when encountered (to make it absolute).
 * </p>
 * <p>
 * The matching is case-insensitive. If you need alternate ways to detect URLs,
 * you can use a combination of {@link RegexTagger}, {@link ReplaceTagger}, or
 * create your own implementation.
 * </p>
 *
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 * <p>
 * If no URLs are found, the target field values (if any) are left intact.
 * </p>
 *
 * <h3>Content source</h3>
 * <p>
 * It is possible to specify a <code>fromField</code>
 * as the source of the text to use instead of using the document content.
 * </p>
 *
 * <p>This class is typically e used as a post-parsing handler only
 * (to ensure we are dealing with text).</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.URLExtractorTagger"
 *     toField="(target field where to store extracted URLs)"
 *     maxReadSize="(max characters to read at once)"
 *     {@nx.include com.norconex.importer.handler.tagger.AbstractCharStreamTagger#attributes}
 *     {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Optional field of text to use. Default uses document content.)
 *   </fieldMatcher>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="URLExtractorTagger" toField="documentURLs">
 *   <restrictTo>
 *     <fieldMatcher>document.contentType</fieldMatcher>
 *     <valueMatcher>application/pdf</valueMatcher>
 *   </restrictTo>
 * </handler>
 * }
 * <p>
 * The above example is used as a post-parse handler. It detects URLs
 * in parsed PDFs and store those URLs in a field call "documentURLs".
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class URLExtractorTagger
        extends AbstractCharStreamTagger implements XMLConfigurable {

    private static final Pattern URL_PATTERN =
            Regex.compileDotAll("\\b(https?:|www\\.)[^\\s]+", true);

    private final TextMatcher fieldMatcher = new TextMatcher();
    private String toField;
    private PropertySetter onSet;
    private int maxReadSize = TextReader.DEFAULT_MAX_READ_SIZE;

    @Override
    protected void tagTextDocument(HandlerDoc doc, Reader input,
            ParseState parseState) throws ImporterHandlerException {

        if (StringUtils.isBlank(toField)) {
            throw new IllegalArgumentException("\"toField\" cannot be blank.");
        }

        Set<String> urls = new HashSet<>();

        if (fieldMatcher.getPattern() == null) {
            extractContentURLs(urls, input);
        } else {
            extractMetadataURLs(urls, doc.getMetadata());
        }

        if (!urls.isEmpty()) {
            PropertySetter.orAppend(onSet).apply(
                    doc.getMetadata(), toField, urls);
        }
    }

    private void extractMetadataURLs(Set<String> urls, Properties meta) {
        for (String text : meta.matchKeys(fieldMatcher).valueList()) {
            extractURLs(urls, text);
        }
    }
    private void extractContentURLs(Set<String> urls, Reader reader)
            throws ImporterHandlerException {
        String text = null;
        try (var tr = new TextReader(reader, maxReadSize)) {
            while ((text = tr.readText()) != null) {
                extractURLs(urls, text);
            }
        } catch (IOException e) {
            throw new ImporterHandlerException("Cannot tag text document.", e);
        }
    }

    private void extractURLs(Set<String> urls, String text) {
        var m = URL_PATTERN.matcher(text);
        while (m.find()) {
            var url = m.group();
            if (!url.startsWith("http")) {
                url = "https://" + url;
            }
            url = StringUtils.stripEnd(url, ".,");
            urls.add(url);
        }
    }

    public String getToField() {
        return toField;
    }
    public void setToField(String toField) {
        this.toField = toField;
    }

    /**
     * Gets field matcher for fields containing text.
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Sets the field matcher for fields containing text.
     * @param fieldMatcher field matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    /**
     * Gets the property setter to use when a value is set.
     * @return property setter
         */
    public PropertySetter getOnSet() {
        return onSet;
    }
    /**
     * Sets the property setter to use when a value is set.
     * @param onSet property setter
         */
    public void setOnSet(PropertySetter onSet) {
        this.onSet = onSet;
    }

    /**
     * Gets the maximum number of characters to read from content for tagging
     * at once. Default is {@link TextReader#DEFAULT_MAX_READ_SIZE}.
     * @return maximum read size
     */
    public int getMaxReadSize() {
        return maxReadSize;
    }
    /**
     * Sets the maximum number of characters to read from content for tagging
     * at once.
     * @param maxReadSize maximum read size
     */
    public void setMaxReadSize(int maxReadSize) {
        this.maxReadSize = maxReadSize;
    }

    @Override
    protected void loadCharStreamTaggerFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
        setOnSet(PropertySetter.fromXML(xml, onSet));
        setToField(xml.getString("@toField", toField));
        setMaxReadSize(xml.getInteger("@maxReadSize", maxReadSize));
    }

    @Override
    protected void saveCharStreamTaggerToXML(XML xml) {
        fieldMatcher.saveToXML(xml.addElement("fieldMatcher"));
        PropertySetter.toXML(xml, getOnSet());
        xml.setAttribute("toField", toField);
        xml.setAttribute("maxReadSize", maxReadSize);
    }
}
