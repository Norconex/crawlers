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

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.transformer.AbstractStringTransformer;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>Strips any content found between a matching start and end strings.  The
 * matching strings are defined in pairs and multiple ones can be specified
 * at once.</p>
 *
 * <p>This class can be used as a pre-parsing (text content-types only)
 * or post-parsing handlers.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.StripBetweenTransformer"
 *     {@nx.include com.norconex.importer.handler.transformer.AbstractStringTransformer#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <!-- multiple stripBetween tags allowed -->
 *   <stripBetween
 *       inclusive="[false|true]">
 *     <startMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching "left" delimiter)
 *     </startMatcher>
 *     <endMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching "right" delimiter)
 *     </endMatcher>
 *   </stripBetween>
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="StripBetweenTransformer">
 *   <stripBetween inclusive="true">
 *     <startMatcher><![CDATA[<!-- SIDENAV_START -->]]></startMatcher>
 *     <endMatcher><![CDATA[<!-- SIDENAV_END -->]]></endMatcher>
 *   </stripBetween>
 * </handler>
 * }
 * <p>
 * The following will strip all text between (and including) these two
 * HTML comments:
 * <code>&lt;!-- SIDENAV_START --&gt;</code> and
 * <code>&lt;!-- SIDENAV_END --&gt;</code>.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class StripBetweenTransformer extends AbstractStringTransformer
        implements XMLConfigurable {

    private final List<StripBetweenDetails> betweens = new ArrayList<>();

    @Override
    protected void transformStringContent(HandlerDoc doc,
            final StringBuilder content, final ParseState parseState,
            final int sectionIndex) {

        for (StripBetweenDetails between : betweens) {
            var leftMatch = between.startMatcher.toRegexMatcher(content);
            while (leftMatch.find()) {
                var rightMatch = between.endMatcher.toRegexMatcher(content);
                if (!rightMatch.find(leftMatch.end())) {
                    break;
                }
                if (between.inclusive) {
                    content.delete(leftMatch.start(), rightMatch.end());
                } else {
                    content.delete(leftMatch.end(), rightMatch.start());
                }
                leftMatch = between.startMatcher.toRegexMatcher(content);
            }
        }
    }

    /**
     * Adds strip between instructions.
     * @param details "strip between" details
         */
    public void addStripBetweenDetails(StripBetweenDetails details) {
        betweens.add(details);
    }
    /**
     * Gets text between instructions.
     * @return "strip between" details
         */
    public List<StripBetweenDetails> getStripBetweenDetailsList() {
        return new ArrayList<>(betweens);
    }

    @Override
    protected void loadStringTransformerFromXML(final XML xml) {
        xml.checkDeprecated("@caseSensitive",
                "startMatcher@ignoreCase and endMatcher@ignoreCase", true);
        xml.checkDeprecated("@inclusive", "stripBetween@inclusive", true);
        for (XML node : xml.getXMLList("stripBetween")) {
            var d = new StripBetweenDetails();
            d.setInclusive(node.getBoolean("@inclusive", false));
            d.startMatcher.loadFromXML(node.getXML("startMatcher"));
            d.endMatcher.loadFromXML(node.getXML("endMatcher"));
            addStripBetweenDetails(d);
        }
    }

    @Override
    protected void saveStringTransformerToXML(final XML xml) {
        for (StripBetweenDetails between : betweens) {
            var bxml = xml.addElement("stripBetween")
                    .setAttribute("inclusive", between.inclusive);
            between.startMatcher.saveToXML(bxml.addElement("startMatcher"));
            between.endMatcher.saveToXML(bxml.addElement("endMatcher"));
        }
    }

    @EqualsAndHashCode
    @ToString
    public static class StripBetweenDetails {
        private final TextMatcher startMatcher = new TextMatcher();
        private final TextMatcher endMatcher = new TextMatcher();
        private boolean inclusive;
        /**
         * Constructor.
         */
        public StripBetweenDetails() {
        }
        /**
         * Constructor.
         * @param startMatcher start matcher
         * @param endMatcher end matcher
         */
        public StripBetweenDetails(
                TextMatcher startMatcher, TextMatcher endMatcher) {
            this.startMatcher.copyFrom(startMatcher);
            this.endMatcher.copyFrom(endMatcher);
        }

        /**
         * Gets the start delimiter matcher for text to strip.
         * @return start delimiter matcher
         */
        public TextMatcher getStartMatcher() {
            return startMatcher;
        }
        /**
         * Sets the start delimiter matcher for text to strip.
         * @param startMatcher start delimiter matcher
         */
        public void setStartMatcher(TextMatcher startMatcher) {
            this.startMatcher.copyFrom(startMatcher);
        }
        /**
         * Gets the end delimiter matcher for text to strip.
         * @return end delimiter matcher
         */
        public TextMatcher getEndMatcher() {
            return endMatcher;
        }
        /**
         * Sets the end delimiter matcher for text to strip.
         * @param endMatcher end delimiter matcher
         */
        public void setEndMatcher(TextMatcher endMatcher) {
            this.endMatcher.copyFrom(endMatcher);
        }

        public boolean isInclusive() {
            return inclusive;
        }
        public void setInclusive(boolean inclusive) {
            this.inclusive = inclusive;
        }
    }
}
