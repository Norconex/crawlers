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

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Replaces an existing metadata value with another one. The "toField"
 * argument is optional.
 * </p>
 * <p>It is possible to only keep values that changed from a replacement and
 * discard others by setting "discardUnchanged" to <code>true</code>.
 * </p>
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 * <p>
 * Not specifying a "toValue" will delete the matches from sources values.
 * </p>
 * <p>
 * Can be used both as a pre-parse or post-parse handler.
 * </p>
 * <p>
 * You can specify whether matches should be made
 * against the whole field value or not (default). You can also specify whether
 * replacement should be attempted on first match only (default) or all
 * occurrences. This last option is only applicable when whole value matching
 * is <code>false</code>.
 * </p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.ReplaceTagger">
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple replace tags allowed -->
 *   <replace
 *       toField="(optional target field name)"
 *       {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *       discardUnchanged="[false|true]">
 *     <fieldMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (one or more matching fields to have their values replaced)
 *     </fieldMatcher>
 *     <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#attributes}>
 *       (one or more source values to replace)
 *     </valueMatcher>
 *     <toValue>(replacement value)</toValue>
 *   </replace>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="ReplaceTagger">
 *   <replace>
 *     <fieldMatcher>fruit</fieldMatcher>
 *     <valueMatcher replaceAll="true">apple</valueMatcher>
 *     <toValue>orange</toValue>
 *   </replace>
 * </handler>
 * }
 * <p>
 * The following example replaces occurrences of "apple" to "orange"
 * in the "fruit" field.
 * </p>
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class ReplaceTagger extends AbstractDocumentTagger {

    private final List<Replacement> replacements = new ArrayList<>();

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {

        // match the keys dealing with values later
        for (Replacement repl : replacements) {
            for (Entry<String, List<String>> en :
                    doc.getMetadata().matchKeys(repl.fieldMatcher).entrySet()) {
                replaceMeta(
                        doc.getMetadata(), repl, en.getKey(), en.getValue());
            }
        }
    }

    private void replaceMeta(Properties metadata, Replacement r,
            String sourceField, List<String> metaValues) {
        List<String> newValues = new ArrayList<>(metaValues.size());
        var toValue = r.toValue == null ? "" : r.toValue;
        for (String metaValue : metaValues) {
            var newValue = r.valueMatcher.replace(metaValue, toValue);
            if (newValue != null && (!r.isDiscardUnchanged()
                    || !Objects.equals(metaValue, newValue))) {
                newValues.add(newValue);
            }
        }

        if (StringUtils.isNotBlank(r.toField)) {
            // set on target field
            PropertySetter.orAppend(r.getOnSet()).apply(
                    metadata, r.toField, newValues);
        } else {
            // overwrite source field
            PropertySetter.REPLACE.apply(metadata, sourceField, newValues);
        }
    }

    public List<Replacement> getReplacements() {
        return Collections.unmodifiableList(replacements);
    }

    /**
     * Adds a replacement.
     * @param replacement the replacement
         */
    public void addReplacement(Replacement replacement) {
        if (replacement != null) {
            replacements.add(replacement);
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class Replacement {
        private final TextMatcher fieldMatcher = new TextMatcher();
        private final TextMatcher valueMatcher = new TextMatcher();
        private String toField;
        private String toValue;
        private PropertySetter onSet;
        private boolean discardUnchanged;
        public Replacement() {
        }
        public Replacement(
                TextMatcher fieldMatcher, TextMatcher valueMatcher,
                String toField, String toValue) {
            this.fieldMatcher.copyFrom(fieldMatcher);
            this.valueMatcher.copyFrom(valueMatcher);
            this.toField = toField;
            this.toValue = toValue;
        }
        public String getToField() {
            return toField;
        }
        public String getToValue() {
            return toValue;
        }
        /**
         * Sets the field to store the replaced value.
         * @param toField field to store the replaced value
         */
        public void setToField(String toField) {
            this.toField = toField;
        }
        /**
         * Sets the replacement value.
         * @param toValue the replacement value
         */
        public void setToValue(String toValue) {
            this.toValue = toValue;
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
         * Gets whether to discard values that did not change as a result
         * of the replacement attempt.
         * @return <code>true</code> if discarding unchanged values
         */
        public boolean isDiscardUnchanged() {
            return discardUnchanged;
        }
        /**
         * Sets whether to discard values that did not change as a result
         * of the replacement attempt.
         * @param discardUnchanged <code>true</code> if discarding unchanged
         *        values
         */
        public void setDiscardUnchanged(boolean discardUnchanged) {
            this.discardUnchanged = discardUnchanged;
        }

        /**
         * Gets field matcher.
         * @return field matcher
         */
        public TextMatcher getFieldMatcher() {
            return fieldMatcher;
        }
        /**
         * Sets field matcher.
         * @param fieldMatcher field matcher
         */
        public void setFieldMatcher(TextMatcher fieldMatcher) {
            this.fieldMatcher.copyFrom(fieldMatcher);
        }

        /**
         * Gets value matcher.
         * @return value matcher
         */
        public TextMatcher getValueMatcher() {
            return valueMatcher;
        }
        /**
         * Sets value matcher.
         * @param valueMatcher value matcher
         */
        public void setValueMatcher(TextMatcher valueMatcher) {
            this.valueMatcher.copyFrom(valueMatcher);
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        for (XML node : xml.getXMLList("replace")) {
            var r = new Replacement();
            r.getFieldMatcher().loadFromXML(node.getXML("fieldMatcher"));
            r.getValueMatcher().loadFromXML(node.getXML("valueMatcher"));
            r.setToValue(node.getString("toValue"));
            r.setToField(node.getString("@toField", null));
            r.setOnSet(PropertySetter.fromXML(node, null));
            r.setDiscardUnchanged(node.getBoolean("@discardUnchanged", false));
            addReplacement(r);
        }
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        for (Replacement replacement : replacements) {
            var rxml = xml.addElement("replace")
                    .setAttribute("toField", replacement.getToField())
                    .setAttribute("discardUnchanged",
                            replacement.isDiscardUnchanged());
            rxml.addElement("toValue", replacement.getToValue());
            PropertySetter.toXML(rxml, replacement.getOnSet());
            replacement.fieldMatcher.saveToXML(rxml.addElement("fieldMatcher"));
            replacement.valueMatcher.saveToXML(rxml.addElement("valueMatcher"));
        }
    }
}
