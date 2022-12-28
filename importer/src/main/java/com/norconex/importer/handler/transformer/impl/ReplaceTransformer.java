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
package com.norconex.importer.handler.transformer.impl;

import java.util.ArrayList;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.transformer.AbstractStringTransformer;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Replaces every occurrences of the given replacements
 * (document content only).</p>
 *
 * <p>This class can be used as a pre-parsing (text content-types only)
 * or post-parsing handlers.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.ReplaceTransformer"
 *     {@nx.include com.norconex.importer.handler.transformer.AbstractStringTransformer#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple replace tags allowed -->
 *   <replace>
 *     <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#attributes}>
 *       (one or more source values to replace)
 *     </valueMatcher>
 *     <toValue>(replacement value)</toValue>
 *   </replace>
 *
 *  </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="ReplaceTransformer">
 *   <replace>
 *     <valueMatcher replaceAll="true">junk food</valueMatcher>
 *     <toValue>healthy food</toValue>
 *   </replace>
 * </handler>
 * }
 * <p>
 * The above example reduces all occurrences of "junk food" with "healthy food".
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class ReplaceTransformer extends AbstractStringTransformer
        implements XMLConfigurable {

    private List<Replacement> replacements = new ArrayList<>();

    @Override
    protected void transformStringContent(HandlerDoc doc,
            final StringBuilder content, final ParseState parseState,
            final int sectionIndex) {

        var text = content.toString();
        content.setLength(0);
        for (Replacement repl : replacements) {
            text = repl.valueMatcher.replace(text, repl.toValue);
        }
        content.append(text);
    }

    public List<Replacement> getReplacements() {
        return replacements;
    }
    public void setReplacements(List<Replacement> replacements) {
        CollectionUtil.setAll(this.replacements, replacements);
    }
    public void addReplacement(Replacement replacement) {
        replacements.add(replacement);
    }

    @Override
    protected void loadStringTransformerFromXML(final XML xml) {
        xml.checkDeprecated("@caseSensitive", true);
        for (XML node : xml.getXMLList("replace")) {
            var r = new Replacement();
            r.getValueMatcher().loadFromXML(node.getXML("valueMatcher"));
            r.setToValue(node.getString("toValue"));
            replacements.add(r);
        }
    }

    @Override
    protected void saveStringTransformerToXML(final XML xml) {
        for (Replacement replacement : replacements) {
            var rxml = xml.addElement("replace");
            rxml.addElement("toValue", replacement.getToValue());
            replacement.valueMatcher.saveToXML(rxml.addElement("valueMatcher"));
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class Replacement {
        private final TextMatcher valueMatcher = new TextMatcher();
        private String toValue;
        public Replacement() {}
        public Replacement(
                TextMatcher valueMatcher, String toValue) {
            this.valueMatcher.copyFrom(valueMatcher);
            this.toValue = toValue;
        }
        public String getToValue() {
            return toValue;
        }
        public void setToValue(String toValue) {
            this.toValue = toValue;
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
}
