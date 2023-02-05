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

import static com.norconex.commons.lang.xml.XPathUtil.attr;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.io.IOUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.condition.ImporterCondition;
import com.norconex.importer.parser.ParseState;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * A condition based on whether the document content (default) or
 * any of the specified metadata fields are blank or inexistent.
 * For metadata fields,
 * control characters (char &lt;= 32) are removed before evaluating whether
 * their values are empty. Dealing with the document content will
 * rather check if it is <code>null</code> or empty (no bytes returned
 * when read).
 * </p>
 *
 * <h3>Multiple fields and values:</h3>
 * <p>
 * By default, ALL values for all fields matched by your field matcher
 * expression must be blank for this condition to be <code>true</code>.
 * You can change the logic to have ANY values to be blank for the condition
 * to be <code>true</code> with {@link #setMatchAnyBlank(boolean)}.
 * </p>
 * <p>
 * If no fields are matched, the conditions is also considered empty
 * (<code>true</code>).
 * </p>
 *
 * {@nx.xml.usage
 * <condition
 *     class="com.norconex.importer.handler.condition.impl.BlankCondition"
 *     matchAnyBlank="[false|true]">
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Optional expression matching fields we want to test if blank,
 *      instead of using the document content.)
 *   </fieldMatcher>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <condition class="BlankCondition">
 *   <fieldMatcher method="regex">(title|dc:title)</fieldMatcher>
 * </condition>
 * }
 * <p>
 * The above example condition will return <code>true</code> if both
 * "title" or "dc:title" are blank.
 * </p>
 * <p>
 */
@SuppressWarnings("javadoc")
@FieldNameConstants
@Data
public class BlankCondition implements ImporterCondition, XMLConfigurable {

    private final TextMatcher fieldMatcher = new TextMatcher();
    private boolean matchAnyBlank;

    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    @Override
    public boolean testDocument(HandlerDoc doc, InputStream input,
            ParseState parseState) throws ImporterHandlerException {

        // do content
        if (fieldMatcher.getPattern() == null) {
            try {
                return IOUtil.isEmpty(input);
            } catch (IOException e) {
                throw new ImporterHandlerException(
                        "Cannot check if document content is blank.", e);
            }
        }

        // If no values returned, call it blank
        var values =
                doc.getMetadata().matchKeys(fieldMatcher).valueList();
        if (values.isEmpty()) {
            return true;
        }

        // if some fields are returned, check them all for blankness
        // one at a time
        if (matchAnyBlank) {
            return values.stream().anyMatch(StringUtils::isBlank);
        }
        return values.stream().allMatch(StringUtils::isBlank);
    }

    @Override
    public void loadFromXML(XML xml) {
        matchAnyBlank = xml.getBoolean(attr(Fields.matchAnyBlank), matchAnyBlank);
        fieldMatcher.loadFromXML(xml.getXML(Fields.fieldMatcher));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute(Fields.matchAnyBlank, matchAnyBlank);
        fieldMatcher.saveToXML(xml.addElement(Fields.fieldMatcher));
    }
}
