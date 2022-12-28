/* Copyright 2021-2022 Norconex Inc.
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
package com.norconex.importer.handler.condition.impl;

import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.condition.AbstractCharStreamCondition;
import com.norconex.importer.handler.condition.AbstractStringCondition;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * A condition based on a text pattern matching a document content
 * (default), or matching specific field(s).
 * When used on very large content, it is possible the pattern matching will
 * be done in chunks, sometimes not achieving expected results.  Consider
 * creating your own condition from {@link AbstractCharStreamCondition}
 * if this is a concern.
 * </p>
 *
 * {@nx.xml.usage
 * <condition class="com.norconex.importer.handler.condition.impl.TextCondition"
 *     {@nx.include com.norconex.importer.handler.condition.AbstractStringCondition#attributes}>
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Optional expression of field to match. Omit to use document content.)
 *   </fieldMatcher>
 *   <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (expression of value to match)
 *   </valueMatcher>
 *
 * </condition>
 * }
 *
 * {@nx.xml.example
 *  <condition class="TextCondition">
 *    <valueMatcher>apple</valueMatcher>
 *  </condition>
 * }
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class TextCondition extends AbstractStringCondition {

    private final TextMatcher fieldMatcher = new TextMatcher();
    private final TextMatcher valueMatcher = new TextMatcher();

    public TextCondition() {
    }
    public TextCondition(TextMatcher valueMatcher) {
        setValueMatcher(valueMatcher);
    }
    public TextCondition(TextMatcher fieldMatcher, TextMatcher valueMatcher) {
        setValueMatcher(valueMatcher);
        setFieldMatcher(fieldMatcher);
    }

    /**
     * Gets the text matcher for content or field values.
     * @return text matcher
     */
    public TextMatcher getValueMatcher() {
        return valueMatcher;
    }
    /**
     * Sets the text matcher for content or field values. Copies it.
     * @param valueMatcher text matcher
     */
    public void setValueMatcher(TextMatcher valueMatcher) {
        this.valueMatcher.copyFrom(valueMatcher);
    }
    /**
     * Gets the text matcher of field names.
     * @return field matcher
     */
    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    /**
     * Sets the text matcher of field names. Copies it.
     * @param fieldMatcher text matcher
     */
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    @Override
    protected boolean testDocument(HandlerDoc doc,
            String input, ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException {
        // content
        if (fieldMatcher.getPattern() == null) {
            return valueMatcher.matches(input);
        }

        // field(s)
        return new PropertyMatcher(
                fieldMatcher, valueMatcher).matches(doc.getMetadata());
    }
    @Override
    protected void loadStringConditionFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML("fieldMatcher"));
        valueMatcher.loadFromXML(xml.getXML("valueMatcher"));
    }
    @Override
    protected void saveStringConditionToXML(XML xml) {
        fieldMatcher.saveToXML(xml.addElement("fieldMatcher"));
        valueMatcher.saveToXML(xml.addElement("valueMatcher"));
    }
}
