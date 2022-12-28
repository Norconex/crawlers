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

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.transformer.AbstractStringTransformer;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Strips any content found after first match found for given pattern.</p>
 *
 * <p>This class can be used as a pre-parsing (text content-types only)
 * or post-parsing handlers.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.transformer.impl.StripAfterTransformer"
 *     inclusive="[false|true]"
 *     {@nx.include com.norconex.importer.handler.transformer.AbstractStringTransformer#attributes}>
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *   <stripAfterMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>>
 *     (expression matching text from which to strip)
 *   </stripAfterMatcher>
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="StripAfterTransformer" inclusive="true">
 *   <stripAfterMatcher><![CDATA[<!-- FOOTER -->]]></stripAfterMatcher>
 * </handler>
 * }
 * <p>
 * The above example will strip all text starting with the following HTML
 * comment and everything after it:
 * <code>&lt;!-- FOOTER --&gt;</code>.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
@Slf4j
public class StripAfterTransformer extends AbstractStringTransformer
        implements XMLConfigurable {

    private boolean inclusive;
    private final TextMatcher stripAfterMatcher = new TextMatcher();

    @Override
    protected void transformStringContent(HandlerDoc doc,
            final StringBuilder content, final ParseState parseState,
            final int sectionIndex) {
        if (stripAfterMatcher.getPattern() == null) {
            LOG.error("No matcher pattern provided.");
            return;
        }

        var m = stripAfterMatcher.toRegexMatcher(content);
        if (m.find()) {
            if (inclusive) {
                content.delete(m.start(), content.length());
            } else {
                content.delete(m.end(), content.length());
            }
        }
    }

    /**
     * Gets the matcher for the text from which to strip content.
     * @return text matcher
         */
    public TextMatcher getStripAfterMatcher() {
        return stripAfterMatcher;
    }
    /**
     * Sets the matcher for the text from which to strip content.
     * @param stripAfterMatcher text matcher
         */
    public void setStripAfterMatcher(TextMatcher stripAfterMatcher) {
        this.stripAfterMatcher.copyFrom(stripAfterMatcher);
    }

    public boolean isInclusive() {
        return inclusive;
    }
    /**
     * Sets whether the match itself should be stripped or not.
     * @param inclusive <code>true</code> to strip start and end text
     */
    public void setInclusive(final boolean inclusive) {
        this.inclusive = inclusive;
    }

    @Override
    protected void loadStringTransformerFromXML(final XML xml) {
        setInclusive(xml.getBoolean("@inclusive", inclusive));
        stripAfterMatcher.loadFromXML(xml.getXML("stripAfterMatcher"));
    }

    @Override
    protected void saveStringTransformerToXML(final XML xml) {
        xml.setAttribute("inclusive", inclusive);
        stripAfterMatcher.saveToXML(xml.addElement("stripAfterMatcher"));
    }
}
