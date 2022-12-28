/* Copyright 2016-2022 Norconex Inc.
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
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.io.TextReader;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractCharStreamTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Counts the number of matches of a given string (or string pattern) and
 * store the resulting value in a field in the specified "toField".
 * </p>
 * <p>
 * If no "fieldMatcher" expression is specified, the document content will be
 * used.  If the "fieldMatcher" matches more than one field, the sum of all
 * matches will be stored as a single value. More often than not,
 * you probably want to set your "countMatcher" to "partial".
 * </p>
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * the count value will be added to the end of the existing value list.
 * It is possible to change this default behavior
 * with {@link #setOnSet(PropertySetter)}.
 * </p>
 *
 * <p>Can be used as a pre-parse tagger on text document only when matching
 * strings on document content, or both as a pre-parse or post-parse handler
 * when the "fieldMatcher" is used.</p>
 *
 * {@nx.xml.usage
 *  <handler class="com.norconex.importer.handler.tagger.impl.CountMatchesTagger"
 *      toField="(target field)"
 *      maxReadSize="(max characters to read at once)"
 *      {@nx.include com.norconex.importer.handler.tagger.AbstractCharStreamTagger#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (optional expression for fields used to count matches)
 *   </fieldMatcher>
 *
 *   <countMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (expression used to count matches)
 *   </countMatcher>
 *
 *  </handler>
 * }
 *
 * {@nx.xml.example
 *  <handler class="CountMatchesTagger" toField="urlSegmentCount">
 *    <fieldMatcher>document.reference</fieldMatcher>
 *    <countMatcher method="regex">/[^/]+</countMatcher>
 *  </handler>
 * }
 * <p>
 * The above will count the number of segments in a URL.
 * </p>
 *
 * @see Pattern
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class CountMatchesTagger extends AbstractCharStreamTagger {

    private TextMatcher fieldMatcher = new TextMatcher();
    private TextMatcher countMatcher = new TextMatcher();
    private String toField;
    private PropertySetter onSet;
    private int maxReadSize = TextReader.DEFAULT_MAX_READ_SIZE;

    @Override
    protected void tagTextDocument(
            HandlerDoc doc, Reader input, ParseState parseState)
            throws ImporterHandlerException {

        // "toField" and value must be present.
        if (StringUtils.isBlank(getToField())) {
            throw new IllegalArgumentException("'toField' cannot be blank.");
        }
        if (countMatcher.getPattern() == null) {
            throw new IllegalArgumentException(
                    "'countMatcher' pattern cannot be null.");
        }

        var count = 0;
        if (fieldMatcher.getPattern() == null) {
            count = countContentMatches(input);
        } else {
            count = countFieldMatches(doc.getMetadata());
        }

        PropertySetter.orAppend(onSet).apply(
                doc.getMetadata(), getToField(), count);
    }

    private int countFieldMatches(Properties metadata) {
        var count = 0;
        for (String value : metadata.matchKeys(fieldMatcher).valueList()) {
            var m = countMatcher.toRegexMatcher(value);
            while (m.find()) {
                count++;
            }
        }
        return count;
    }
    private int countContentMatches(Reader reader)
            throws ImporterHandlerException {
        var count = 0;
        String text = null;
        try (var tr = new TextReader(reader, maxReadSize)) {
            while ((text = tr.readText()) != null) {
                var m = countMatcher.toRegexMatcher(text);
                while (m.find()) {
                    count++;
                }
            }
        } catch (IOException e) {
            throw new ImporterHandlerException("Cannot tag text document.", e);
        }
        return count;
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

    /**
     * Gets the field matcher.
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Sets the field matcher.
     * @param fieldMatcher field matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher = fieldMatcher;
    }

    /**
     * Gets the count matcher.
     * @return count matcher
     */
    public TextMatcher getCountMatcher() {
        return countMatcher;
    }
    /**
     * Sets the count matcher.
     * @param countMatcher count matcher
     */
    public void setCountMatcher(TextMatcher countMatcher) {
        this.countMatcher = countMatcher;
    }

    /**
     * Sets the target field.
     * @return target field
     */
    public String getToField() {
        return toField;
    }
    /**
     * Gets the target field.
     * @param toField target field
     */
    public void setToField(String toField) {
        this.toField = toField;
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

    @Override
    protected void loadCharStreamTaggerFromXML(XML xml) {
        setOnSet(PropertySetter.fromXML(xml, onSet));
        setToField(xml.getString("@toField", toField));
        setMaxReadSize(xml.getInteger("@maxReadSize", maxReadSize));
        fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
        countMatcher.loadFromXML(xml.getXML("countMatcher"));
    }
    @Override
    protected void saveCharStreamTaggerToXML(XML xml) {
        PropertySetter.toXML(xml, getOnSet());
        xml.setAttribute("toField", toField);
        xml.setAttribute("maxReadSize", maxReadSize);
        fieldMatcher.saveToXML(xml.addElement("fieldMatcher"));
        countMatcher.saveToXML(xml.addElement("countMatcher"));
    }
}
