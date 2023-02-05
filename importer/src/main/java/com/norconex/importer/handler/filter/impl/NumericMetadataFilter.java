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
package com.norconex.importer.handler.filter.impl;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.Operator;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.AbstractDocumentFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.FieldNameConstants;
import lombok.extern.slf4j.Slf4j;
/**
 * <p>
 * Accepts or rejects a document based on the numeric value(s) of matching
 * metadata fields, supporting decimals. If multiple values are found for a
 * field, only one of them needs to match for this filter to take effect.
 * If the value is not a valid number, it is considered not to be matching.
 * The decimal character is expected to be a dot.
 * To reject decimals or to deal with
 * non-numeric fields in your own way, you can use {@link TextFilter}.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.filter.impl.NumericMetadataFilter"
 *   {@nx.include com.norconex.importer.handler.filter.AbstractDocumentFilter#attributes}>
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (expression matching numeric fields to filter)
 *   </fieldMatcher>
 *
 *   <!-- Use one or two (for ranges) conditions,
 *        where possible operators are:
 *
 *          gt -> greater than
 *          ge -> greater equal
 *          lt -> lower than
 *          le -> lowe equal
 *          eq -> equals
 *   -->
 *
 *   <condition operator="[gt|ge|lt|le|eq]" number="(number)" />
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="NumericMetadataFilter" onMatch="include">
 *   <fieldMatcher>age</fieldMatcher>
 *   <condition operator="ge" number="20" />
 *   <condition operator="lt" number="30" />
 *  </handler>
 * }
 * <p>
 * Let's say you are importing customer profile documents
 * and you have a field called "age" and you need to only consider documents
 * for customers in their twenties (greater or equal to
 * 20, but lower than 30). The above example would achieve that.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@FieldNameConstants
@Slf4j
public class NumericMetadataFilter extends AbstractDocumentFilter {

    private final TextMatcher fieldMatcher = new TextMatcher();
    private final List<Condition> conditions = new ArrayList<>(2);

    public NumericMetadataFilter() {}

    /**
     * Constructor.
     * @param fieldMatcher matcher for fields on which to apply date filtering
     */
    public NumericMetadataFilter(TextMatcher fieldMatcher) {
        this(fieldMatcher, OnMatch.INCLUDE);
    }
    /**
     *
     * @param fieldMatcher matcher for fields on which to apply date filtering
     * @param onMatch include or exclude on match
     */
    public NumericMetadataFilter(TextMatcher fieldMatcher, OnMatch onMatch) {
        this.fieldMatcher.copyFrom(fieldMatcher);
        setOnMatch(onMatch);
    }

    public TextMatcher getFieldMatcher() {
        return fieldMatcher;
    }
    public void setFieldMatcher(TextMatcher fieldMatcher) {
        this.fieldMatcher.copyFrom(fieldMatcher);
    }

    public List<Condition> getConditions() {
        return Collections.unmodifiableList(conditions);
    }
    public void setConditions(Condition... conditions) {
        CollectionUtil.setAll(this.conditions, conditions);
    }
    public void addCondition(Operator operator, double number) {
        conditions.add(new Condition(operator, number));
    }

    @Override
    protected boolean isDocumentMatched(
            HandlerDoc doc, InputStream input, ParseState parseState)
                    throws ImporterHandlerException {

        if (fieldMatcher.getPattern() == null) {
            throw new IllegalArgumentException(
                    "\"fieldMatcher\" pattern cannot be empty.");
        }
        for (String value : doc.getMetadata().matchKeys(
                fieldMatcher).valueList()) {
            if (meetsAllConditions(value)) {
                return true;
            }
        }
        return false;
    }

    private boolean meetsAllConditions(String fieldValue) {
        if (!NumberUtils.isCreatable(fieldValue)) {
            return false;
        }
        var fieldNumber = NumberUtils.toDouble(fieldValue);
        for (Condition condition : conditions) {
            if (!condition.getOperator().evaluate(
                    fieldNumber, condition.getNumber())) {
                return false;
            }
        }
        return true;
    }

    @Override
    protected void loadFilterFromXML(XML xml) {
        fieldMatcher.loadFromXML(xml.getXML(Fields.fieldMatcher));
        var nodes = xml.getXMLList("condition");
        for (XML node : nodes) {
            var op = node.getString("@operator", null);
            var num = node.getString("@number", null);
            var isValid = true;
            if (StringUtils.isBlank(op) || StringUtils.isBlank(num)) {
                LOG.warn("Both \"operator\" and \"number\" must be provided.");
                isValid = false;
            } else if (Operator.of(op) == null) {
                LOG.warn("Unsupported operator: {}", op);
                isValid = false;
            }
            if (!isValid) {
                break;
            }
            var number = NumberUtils.toDouble(num);
            addCondition(Operator.of(op), number);
        }
    }

    @Override
    protected void saveFilterToXML(XML xml) {
        for (Condition condition : conditions) {
            xml.addElement("condition")
                    .setAttribute("operator", condition.operator.toString())
                    .setAttribute("number", condition.number);
        }
        fieldMatcher.saveToXML(xml.addElement(Fields.fieldMatcher));
    }

    @Data
    public static class Condition {
        private final Operator operator;
        private final double number;
    }
}

