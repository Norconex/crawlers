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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractStringTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * Extracts field names and their values with regular expression.
 * This is done by using
 * match groups in your regular expressions (parenthesis).  For each pattern
 * you define, you can specify which match group hold the field name and
 * which one holds the value.
 * Specifying a field match group is optional if a <code>field</code>
 * is provided.  If no match groups are specified, a <code>field</code>
 * is expected.
 * </p>
 *
 * <p>
 * If "fieldMatcher" is specified, it will use content from matching fields and
 * storing all text extracted into the target field, multi-value.
 * Else, the document content is used.
 * </p>
 *
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 *
 * <p>
 * This class can be used as a pre-parsing handler on text documents only
 * or a post-parsing handler.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.RegexTagger"
 *     {@nx.include com.norconex.importer.handler.tagger.AbstractStringTagger#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (optional expression matching source fields on which to perform extraction)
 *   </fieldMatcher>
 *
 *   <!-- multiple pattern tags allowed -->
 *   <pattern
 *       {@nx.include com.norconex.commons.lang.text.RegexFieldValueExtractor#attributes}>
 *     (regular expression)
 *   </pattern>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="RegexTagger" >
 *   <pattern toField="emails">
 *     [A-Za-z0-9+_.-]+?@[a-zA-Z0-9.-]+
 *   </pattern>
 *   <pattern fieldGroup="1" valueGroup="2"><![CDATA[
 *     <tr><td class="label">(.*?)</td><td class="value">(.*?)</td></tr>
 *   ]]></pattern>
 * </handler>
 * }
 * <p>
 * The first pattern in the above example extracts what look like email
 * addresses in to an "email" field (simplified regex). The second pattern
 * extracts field names and values from "label" and "value" cells on
 * a given HTML table.
 * </p>
 *
 * @see RegexFieldValueExtractor
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class RegexTagger
        extends AbstractStringTagger implements XMLConfigurable {

    private final TextMatcher fieldMatcher = new TextMatcher();
    private final List<RegexFieldValueExtractor> patterns = new ArrayList<>();

    @Override
    protected void tagStringContent(HandlerDoc doc, StringBuilder content,
            ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException {
        if (fieldMatcher.getPattern() == null) {
            RegexFieldValueExtractor.extractFieldValues(
                    doc.getMetadata(), content, patterns);
        } else {
            for (String value :
                    doc.getMetadata().matchKeys(fieldMatcher).valueList()) {
                RegexFieldValueExtractor.extractFieldValues(
                        doc.getMetadata(), value, patterns);
            }
        }
    }

    /**
     * Adds a pattern that will extract the whole text matched into
     * given field.
     * @param field target field to store the matching pattern.
     * @param pattern the pattern
     */
    public void addPattern(String field, String pattern) {
        if (StringUtils.isAnyBlank(pattern, field)) {
            return;
        }
        addPattern(new RegexFieldValueExtractor(pattern).setToField(field));
    }
    /**
     * Adds a new pattern, which will extract the value from the specified
     * group index upon matching.
     * @param field target field to store the matching pattern.
     * @param pattern the pattern
     * @param valueGroup which pattern group to return.
     */
    public void addPattern(String field, String pattern, int valueGroup) {
        if (StringUtils.isAnyBlank(pattern, field)) {
            return;
        }
        addPattern(new RegexFieldValueExtractor(
                pattern).setToField(field).setValueGroup(valueGroup));
    }
    /**
     * Adds one or more pattern that will extract matching field names/values.
     * @param pattern field extractor pattern
     */
    public void addPattern(RegexFieldValueExtractor... pattern) {
        if (ArrayUtils.isNotEmpty(pattern)) {
            patterns.addAll(Arrays.asList(pattern));
        }
    }
    /**
     * Sets one or more patterns that will extract matching field names/values.
     * Clears previously set pattterns.
     * @param patterns field extractor pattern
     */
    public void setPattern(RegexFieldValueExtractor... patterns) {
        CollectionUtil.setAll(this.patterns, patterns);
    }
    /**
     * Gets the patterns used to extract matching field names/values.
     * @return patterns
     */
    public List<RegexFieldValueExtractor> getPatterns() {
        return Collections.unmodifiableList(patterns);
    }

    /**
     * Gets source field matcher for fields on which to extract fields/values.
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Sets source field matcher for fields on which to extract fields/values.
     * @param fieldMatcher field matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    @Override
    protected void loadStringTaggerFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
        var nodes = xml.getXMLList("pattern");
        for (XML node : nodes) {
            node.checkDeprecated("@caseSensitive", "ignoreCase", true);
            var ex = new RegexFieldValueExtractor();
            ex.loadFromXML(node);
            addPattern(ex);
        }
    }

    @Override
    protected void saveStringTaggerToXML(XML xml) {
        xml.addElement("fieldMatcher", fieldMatcher);
        for (RegexFieldValueExtractor rfe : patterns) {
            rfe.saveToXML(xml.addElement("pattern"));
        }
    }
}
