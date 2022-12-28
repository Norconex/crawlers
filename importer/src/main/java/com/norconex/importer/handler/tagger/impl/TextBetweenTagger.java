/* Copyright 2010-2022 Norconex Inc.
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
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
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
 * <p>Extracts and add values found between a matching start and
 * end strings to a document metadata field.
 * The matching string end-points are defined in pairs and multiple ones
 * can be specified at once. The field specified for a pair of end-points
 * is considered a multi-value field.</p>
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
 * <p>
 * This class can be used as a pre-parsing handler on text documents only
 * or a post-parsing handler.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.TextBetweenTagger"
 *     {@nx.include com.norconex.importer.handler.tagger.AbstractStringTagger#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple textBetween tags allowed -->
 *   <textBetween
 *       toField="(target field name)"
 *       inclusive="[false|true]"
 *       {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *     <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (optional expression matching fields to perform extraction on)
 *     </fieldMatcher>
 *     <startMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching "left" delimiter)
 *     </startMatcher>
 *     <endMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching "right" delimiter)
 *     </endMatcher>
 *   </textBetween>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="TextBetweenTagger">
 *   <textBetween toField="content">
 *     <startMatcher>OPEN</startMatcher>
 *     <endMatcher>CLOSE</endMatcher>
 *   </textBetween>
 * </handler>
 * }
 * <p>
 * The above example extract the content between "OPEN" and
 * "CLOSE" strings, excluding these strings, and store it in a "content"
 * field.
 * </p>
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class TextBetweenTagger
        extends AbstractStringTagger implements XMLConfigurable {

    private final List<TextBetweenDetails> betweens = new ArrayList<>();

    @Override
    protected void tagStringContent(HandlerDoc doc, StringBuilder content,
            ParseState parseState, int sectionIndex)
                    throws ImporterHandlerException {
        for (TextBetweenDetails between : betweens) {
            if (between.fieldMatcher.getPattern() == null) {
                betweenContent(between, content, doc.getMetadata());
            } else {
                betweenMetadata(between, doc.getMetadata());
            }
        }
    }

    private void betweenContent(TextBetweenDetails between,
            StringBuilder content, Properties metadata) {
        PropertySetter.orAppend(between.onSet).apply(metadata,
                between.toField, betweenText(between, content.toString()));
    }
    private void betweenMetadata(
            TextBetweenDetails between, Properties metadata) {
        List<String> allTargetValues = new ArrayList<>();
        for (Entry<String, List<String>> en :
                metadata.matchKeys(between.fieldMatcher).entrySet()) {
            var fromField = en.getKey();
            var sourceValues = en.getValue();
            List<String> targetValues = new ArrayList<>();
            for (String sourceValue : sourceValues) {
                targetValues.addAll(betweenText(between, sourceValue));
            }

            // if toField is blank, we overwrite the source and do not
            // carry values further.
            if (StringUtils.isBlank(between.getToField())) {
                // overwrite source field
                PropertySetter.REPLACE.apply(
                        metadata, fromField, targetValues);
            } else {
                allTargetValues.addAll(targetValues);
            }
        }
        if (StringUtils.isNotBlank(between.getToField())) {
            // set on target field
            PropertySetter.orAppend(between.onSet).apply(
                    metadata, between.toField, allTargetValues);
        }
    }
    private List<String> betweenText(
            TextBetweenDetails between, String text) {
        List<Pair<Integer, Integer>> matches = new ArrayList<>();
        var leftMatch = between.startMatcher.toRegexMatcher(text);
        while (leftMatch.find()) {
            var rightMatch = between.endMatcher.toRegexMatcher(text);
            if (!rightMatch.find(leftMatch.end())) {
                break;
            }
            if (between.inclusive) {
                matches.add(new ImmutablePair<>(
                        leftMatch.start(), rightMatch.end()));
            } else {
                matches.add(new ImmutablePair<>(
                        leftMatch.end(), rightMatch.start()));
            }
        }
        List<String> values = new ArrayList<>();
        for (var i = matches.size() -1; i >= 0; i--) {
            var matchPair = matches.get(i);
            var value = text.substring(
                    matchPair.getLeft(), matchPair.getRight());
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    /**
     * Adds text between instructions.
     * @param details "text between" details
     */
    public void addTextBetweenDetails(TextBetweenDetails details) {
        betweens.add(details);
    }
    /**
     * Gets text between instructions.
     * @return "text between" details
         */
    public List<TextBetweenDetails> getTextBetweenDetailsList() {
        return new ArrayList<>(betweens);
    }

    @Override
    protected void loadStringTaggerFromXML(XML xml) {
        xml.checkDeprecated("@caseSensitive",
                "startMatcher@ignoreCase and endMatcher@ignoreCase", true);
        xml.checkDeprecated("@name", "toField", true);

        var nodes = xml.getXMLList("textBetween");
        for (XML node : nodes) {
            var tbd = new TextBetweenDetails();
            tbd.setToField(node.getString("@toField", null));
            tbd.setInclusive(node.getBoolean("@inclusive", false));
            tbd.setOnSet(PropertySetter.fromXML(node, null));
            tbd.fieldMatcher.loadFromXML(node.getXML("fieldMatcher"));
            tbd.startMatcher.loadFromXML(node.getXML("startMatcher"));
            tbd.endMatcher.loadFromXML(node.getXML("endMatcher"));
            addTextBetweenDetails(tbd);
        }
    }

    @Override
    protected void saveStringTaggerToXML(XML xml) {
        for (TextBetweenDetails between : betweens) {
            var bxml = xml.addElement("textBetween")
                    .setAttribute("toField", between.toField)
                    .setAttribute("inclusive", between.inclusive);
            PropertySetter.toXML(bxml, between.getOnSet());
            between.fieldMatcher.saveToXML(bxml.addElement("fieldMatcher"));
            between.startMatcher.saveToXML(bxml.addElement("startMatcher"));
            between.endMatcher.saveToXML(bxml.addElement("endMatcher"));
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class TextBetweenDetails {
        private final TextMatcher fieldMatcher = new TextMatcher();
        private final TextMatcher startMatcher = new TextMatcher();
        private final TextMatcher endMatcher = new TextMatcher();
        private String toField;
        private boolean inclusive;
        private PropertySetter onSet;

        public TextBetweenDetails() {}
        /**
         * Constructor.
         * @param toField target field
         * @param fieldMatcher optional source fields
         * @param startMatcher start matcher
         * @param endMatcher end matcher
         */
        public TextBetweenDetails(
                String toField, TextMatcher fieldMatcher,
                TextMatcher startMatcher, TextMatcher endMatcher) {
            this.toField = toField;
            this.fieldMatcher.copyFrom(fieldMatcher);
            this.startMatcher.copyFrom(startMatcher);
            this.endMatcher.copyFrom(endMatcher);
        }

        /**
         * Gets field matcher for fields on which to extract values.
         * @return field matcher
         */
        public TextMatcher getFieldMatcher() {
            return fieldMatcher;
        }
        /**
         * Sets field matcher for fields on which to extract values.
         * @param fieldMatcher field matcher
         */
        public void setFieldMatcher(TextMatcher fieldMatcher) {
            this.fieldMatcher.copyFrom(fieldMatcher);
        }
        /**
         * Gets the start delimiter matcher for text to extract.
         * @return start delimiter matcher
         */
        public TextMatcher getStartMatcher() {
            return startMatcher;
        }
        /**
         * Sets the start delimiter matcher for text to extract.
         * @param startMatcher start delimiter matcher
         */
        public void setStartMatcher(TextMatcher startMatcher) {
            this.startMatcher.copyFrom(startMatcher);
        }
        /**
         * Gets the end delimiter matcher for text to extract.
         * @return end delimiter matcher
         */
        public TextMatcher getEndMatcher() {
            return endMatcher;
        }
        /**
         * Sets the end delimiter matcher for text to extract.
         * @param endMatcher end delimiter matcher
         */
        public void setEndMatcher(TextMatcher endMatcher) {
            this.endMatcher.copyFrom(endMatcher);
        }
        /**
         * Sets the target field for extracted text.
         * @param toField target field
         */
        public void setToField(String toField) {
            this.toField = toField;
        }

        public boolean isInclusive() {
            return inclusive;
        }
        public void setInclusive(boolean inclusive) {
            this.inclusive = inclusive;
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

        public String getToField() {
            return toField;
        }
    }
}
