/* Copyright 2020-2022 Norconex Inc.
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
package com.norconex.importer.handler.filter.impl;

import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.AbstractCharStreamFilter;
import com.norconex.importer.handler.filter.AbstractDocumentFilter;
import com.norconex.importer.handler.filter.AbstractStringFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;

/**
 * <p>Filters a document based on a text pattern in a document content
 * (default), or matching fields specified.
 * When used on very large content, it is possible the pattern matching will
 * be done in chunks, sometimes not achieving expected results.  Consider
 * using {@link AbstractCharStreamFilter} if this is a concern.
 * Refer to {@link AbstractDocumentFilter} for the inclusion/exclusion logic.
 * </p>
 * <p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.filter.impl.RegexContentFilter"
 *     {@nx.include com.norconex.importer.handler.filter.AbstractStringFilter#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Optional expression of field to match. Omit to use document content.)
 *   </fieldMatcher>
 *   <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (expression of value to match)
 *   </valueMatcher>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 *  <handler class="TextFilter" onMatch="include" >
 *      <valueMatcher>apple</valueMatcher>
 *  </handler>
 * }
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@FieldNameConstants
public class TextFilter extends AbstractStringFilter {

    //TODO use @nx.block and @nx.include to insert inclusion logic for above
    // documentation? ( Refer to {@link AbstractDocumentFilter}
    // for the inclusion/exclusion logic.)

    private final TextMatcher valueMatcher = new TextMatcher();
    private final TextMatcher fieldMatcher = new TextMatcher();

    public TextFilter() {
    }
    public TextFilter(TextMatcher valueMatcher) {
        setValueMatcher(valueMatcher);
    }
    public TextFilter(TextMatcher valueMatcher, OnMatch onMatch) {
        setValueMatcher(valueMatcher);
        setOnMatch(onMatch);
    }
    public TextFilter(TextMatcher fieldMatcher, TextMatcher valueMatcher) {
        setValueMatcher(valueMatcher);
        setFieldMatcher(fieldMatcher);
    }
    public TextFilter(TextMatcher fieldMatcher, TextMatcher valueMatcher,
            OnMatch onMatch) {
        setValueMatcher(valueMatcher);
        setFieldMatcher(fieldMatcher);
        setOnMatch(onMatch);
    }

    /**
     * Gets the text matcher for field values.
     * @return text matcher
     */
    public TextMatcher getValueMatcher() {
        return valueMatcher;
    }
    /**
     * Sets the text matcher for field values. Copies it.
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
    protected boolean isStringContentMatching(HandlerDoc doc,
            StringBuilder content, ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException {

        // content
        if (fieldMatcher.getPattern() == null) {
            return valueMatcher.matches(content.toString());
        }

        // field(s)
        return new PropertyMatcher(fieldMatcher, valueMatcher).matches(
                doc.getMetadata());
    }

    @Override
    protected void saveStringFilterToXML(XML xml) {
        fieldMatcher.saveToXML(xml.addElement(Fields.fieldMatcher));
        valueMatcher.saveToXML(xml.addElement(Fields.valueMatcher));
    }
    @Override
    protected void loadStringFilterFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML(Fields.fieldMatcher));
        valueMatcher.loadFromXML(xml.getXML(Fields.valueMatcher));
    }

}
