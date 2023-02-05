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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.io.IOUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.AbstractDocumentFilter;
import com.norconex.importer.parser.ParseState;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
/**
 * <p>Accepts or rejects a document based on whether its content (default) or
 * any of the specified metadata fields are empty or not.  For metadata fields,
 * control characters (char &lt;= 32) are removed before evaluating whether
 * their values are empty.</p>
 *
 * <h3>Filtering on multiple fields:</h3>
 * <p>
 * It is important to note that when your field matcher expression matches
 * more than one field, only one of the matched fields needs to be empty
 * to trigger a match. If no fields are matched, it is also considered empty.
 * If you expect some fields to be present and they are not, they will not
 * be evaluated thus are not considered empty.  To make sure a multiple fields
 * are tested properly, used multiple instances of {@link EmptyFilter}, with
 * field matching matching only one field in each.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.filter.impl.EmptyFilter"
 *     {@nx.include com.norconex.importer.handler.filter.AbstractDocumentFilter#attributes}>
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (optional expression matching fields we want to test for emptiness)
 *   </fieldMatcher>
 * </handler>
 * }
 *
 * {@nx.xml.example
 *  <handler class="EmptyFilter" onMatch="exclude">
 *    <fieldMatcher method="regex">(title|dc:title)</fieldMatcher>
 *  </handler>
 * }
 * <p>
 * The above example excludes documents without titles.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants
public class EmptyFilter extends AbstractDocumentFilter {

    private final TextMatcher fieldMatcher = new TextMatcher();

    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    @Override
    protected boolean isDocumentMatched(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException {

        // do content
        if (fieldMatcher.getPattern() == null) {
            try {
                return IOUtil.isEmpty(input);
            } catch (IOException e) {
                throw new ImporterHandlerException(
                        "Cannot check if document is empty.", e);
            }
        }

        // If no values returned, call it empty
        var entrySet =
                doc.getMetadata().matchKeys(fieldMatcher).entrySet();
        if (entrySet.isEmpty()) {
            return true;
        }

        // if fome fields are returned, check them all for emptiness
        // one at a time
        for (Entry<String, List<String>> en :
                doc.getMetadata().matchKeys(fieldMatcher).entrySet()) {
            var isPropEmpty = true;
            for (String value : en.getValue()) {
                if (!StringUtils.isBlank(StringUtils.trim(value))) {
                    isPropEmpty = false;
                    break;
                }
            }
            if (isPropEmpty) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void loadFilterFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML(Fields.fieldMatcher));
    }

    @Override
    protected void saveFilterToXML(XML xml) {
        fieldMatcher.saveToXML(xml.addElement(Fields.fieldMatcher));
    }
}

