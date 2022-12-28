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

import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Scanner;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

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
 * <p>Splits an existing metadata value into multiple values based on a given
 * value separator (the separator gets discarded).  The "toField" argument
 * is optional (the same field will be used to store the splits if no
 * "toField" is specified"). Duplicates are removed.</p>
 * <p>Can be used both as a pre-parse (metadata or text content) or
 * post-parse handler.</p>
 * <p>
 * If no "fieldMatcher" expression is specified, the document content will be
 * used.  If the "fieldMatcher" matches more than one field, they will all
 * be split and stored in the same multi-value metadata field.
 * </p>
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.SplitTagger"
 *     {@nx.include com.norconex.importer.handler.tagger.AbstractCharStreamTagger#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple split tags allowed -->
 *   <split
 *       toField="targetFieldName"
 *       {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *     <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (one or more matching fields to split)
 *     </fieldMatcher>
 *     <separator regex="[false|true]">(separator value)</separator>
 *   </split>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="SplitTagger">
 *   <split>
 *     <fieldMatcher>myField</fieldMatcher>
 *     <separator regex="true">\s*,\s*</separator>
 *   </split>
 * </handler>
 * }
 * <p>
 * The above example splits a single value field holding a comma-separated
 * list into multiple values.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class SplitTagger extends AbstractCharStreamTagger {

    private final List<SplitDetails> splits = new ArrayList<>();

    @Override
    protected void tagTextDocument(
            HandlerDoc doc, Reader input, ParseState parseState)
            throws ImporterHandlerException {

        for (SplitDetails split : splits) {

            if (split.fieldMatcher.getPattern() == null) {
                splitContent(split, input, doc.getMetadata());
            } else {
                splitMetadata(split, doc.getMetadata());
            }
        }
    }

    private void splitContent(
            SplitDetails split, Reader input, Properties metadata) {

        var delim = split.getSeparator();
        if (!split.isSeparatorRegex()) {
            delim = Pattern.quote(delim);
        }
        List<String> targetValues = new ArrayList<>();
        @SuppressWarnings("resource") // input stream controlled by caller.
        var scanner = new Scanner(input).useDelimiter(delim);
        while (scanner.hasNext()) {
            targetValues.add(scanner.next());
        }
        PropertySetter.orAppend(split.getOnSet()).apply(
                metadata, split.getToField(), targetValues);
    }
    private void splitMetadata(SplitDetails split, Properties metadata) {

        List<String> allTargetValues = new ArrayList<>();
        for (Entry<String, List<String>> en :
                metadata.matchKeys(split.fieldMatcher).entrySet()) {
            var fromField = en.getKey();
            var sourceValues = en.getValue();
            List<String> targetValues = new ArrayList<>();
            for (String sourceValue : sourceValues) {
                if (split.isSeparatorRegex()) {
                    targetValues.addAll(regexSplit(
                            sourceValue, split.getSeparator()));
                } else {
                    targetValues.addAll(regularSplit(
                            sourceValue, split.getSeparator()));
                }
            }

            // toField is blank, we overwrite the source and do not
            // carry values further.
            if (StringUtils.isBlank(split.getToField())) {
                // overwrite source field
                PropertySetter.REPLACE.apply(
                        metadata, fromField, targetValues);
            } else {
                allTargetValues.addAll(targetValues);
            }
        }
        if (StringUtils.isNotBlank(split.getToField())) {
            // set on target field
            PropertySetter.orAppend(split.getOnSet()).apply(
                    metadata, split.getToField(), allTargetValues);
        }
    }

    private List<String> regexSplit(String metaValue, String separator) {
        var values = metaValue.split(separator);
        List<String> cleanValues = new ArrayList<>();
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                cleanValues.add(value);
            }
        }
        return cleanValues;
    }
    private List<String> regularSplit(String metaValue, String separator) {
        return Arrays.asList(
                StringUtils.splitByWholeSeparator(metaValue, separator));
    }

    public List<SplitDetails> getSplitDetailsList() {
        return Collections.unmodifiableList(splits);
    }
    public void removeSplitDetails(String fromField) {
        List<SplitDetails> toRemove = new ArrayList<>();
        for (SplitDetails split : splits) {
            if (split.getFieldMatcher().matches(fromField)) {
                toRemove.add(split);
            }
        }
        synchronized (splits) {
            splits.removeAll(toRemove);
        }
    }

    public void addSplitDetails(SplitDetails sd) {
        if (sd != null) {
            splits.add(sd);
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class SplitDetails {
        private final TextMatcher fieldMatcher = new TextMatcher();
        private String toField;
        private PropertySetter onSet;
        private String separator;
        private boolean separatorRegex;

        public SplitDetails() {}
        /**
         * Constructor.
         * @param fieldMatcher source field matcher
         * @param toField target field
         * @param separator split separator
         */
        public SplitDetails(
                TextMatcher fieldMatcher, String toField, String separator) {
            this(fieldMatcher, toField, separator, false);
        }
        /**
         * Constructor.
         * @param fieldMatcher source field matcher
         * @param toField target field
         * @param separator split separator
         * @param separatorRegex whether the separator is a regular expression
         */
        public SplitDetails(TextMatcher fieldMatcher, String toField,
                String separator, boolean separatorRegex) {
            this.fieldMatcher.copyFrom(fieldMatcher);
            this.toField = toField;
            this.separator = separator;
            this.separatorRegex = separatorRegex;
        }

        /**
         * Gets field matcher for fields to split.
         * @return field matcher
         */
        public TextMatcher getFieldMatcher() {
            return fieldMatcher;
        }
        /**
         * Sets the field matcher for fields to split.
         * @param fieldMatcher field matcher
         */
        public void setFieldMatcher(TextMatcher fieldMatcher) {
            this.fieldMatcher.copyFrom(fieldMatcher);
        }
        public String getToField() {
            return toField;
        }
        public void setToField(String toField) {
            this.toField = toField;
        }
        public String getSeparator() {
            return separator;
        }
        public void setSeparator(String separator) {
            this.separator = separator;
        }
        /**
         * Gets whether the separator value is a regular expression.
         * @return <code>true</code> if a regular expression.
         */
        public boolean isSeparatorRegex() {
            return separatorRegex;
        }
        /**
         * Sets whether the separator value is a regular expression.
         * @param regex <code>true</code> if a regular expression.
         */
        public void setSeparatorRegex(boolean regex) {
            separatorRegex = regex;
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
    }

    @Override
    protected void loadCharStreamTaggerFromXML(XML xml) {
        for (XML node : xml.getXMLList("split")) {
            var sd = new SplitDetails();
            sd.fieldMatcher.loadFromXML(node.getXML("fieldMatcher"));
            sd.setToField(node.getString("@toField", null));
            sd.setSeparator(node.getString("separator"));
            sd.setSeparatorRegex(node.getBoolean("separator/@regex", false));
            sd.setOnSet(PropertySetter.fromXML(node, null));
            addSplitDetails(sd);
        }
    }

    @Override
    protected void saveCharStreamTaggerToXML(XML xml) {
        for (SplitDetails split : splits) {
            var sxml = xml.addElement("split")
                    .setAttribute("toField", split.getToField());
            sxml.addElement("separator", split.getSeparator())
                    .setAttribute("regex", split.isSeparatorRegex());
            PropertySetter.toXML(sxml, split.getOnSet());
            split.fieldMatcher.saveToXML(sxml.addElement("fieldMatcher"));
        }
    }
}
