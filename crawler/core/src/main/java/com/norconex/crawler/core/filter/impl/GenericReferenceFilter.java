/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.crawler.core.filter.impl;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.filter.DocumentFilter;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.handler.filter.OnMatchFilter;

import lombok.EqualsAndHashCode;
import lombok.ToString;
/**
 * <p>
 * Filters URL based on a matching expression.
 * </p>
 *
 * {@nx.xml.usage
 * <filter class="com.norconex.crawler.core.filter.impl.GenericReferenceFilter"
 *     onMatch="[include|exclude]">
 *   <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Expression matching the document reference.)
 *   </valueMatcher>
 * </filter>
 * }
 *
 * {@nx.xml.example
 * <filter class="GenericReferenceFilter" onMatch="exclude">
 *   <valueMatcher method="regex">.*&#47;login/.*</valueMatcher>
 * </filter>
 * }
 * <p>
 * The above will reject documents having "/login/" in their reference.
 * </p>
 * @see Pattern
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class GenericReferenceFilter implements
        OnMatchFilter,
        ReferenceFilter,
        DocumentFilter,
        MetadataFilter,
        XMLConfigurable {

    private OnMatch onMatch;
    private final TextMatcher valueMatcher = new TextMatcher();

    public GenericReferenceFilter() {
        this(null, OnMatch.INCLUDE);
    }
    public GenericReferenceFilter(TextMatcher valueMatcher) {
        this(valueMatcher, OnMatch.INCLUDE);
    }
    public GenericReferenceFilter(
            TextMatcher valueMatcher,
            OnMatch onMatch) {
        setValueMatcher(valueMatcher);
        setOnMatch(onMatch);
    }

    /**
     * Gets the value matcher.
     * @return value matcher
     */
    public TextMatcher getValueMatcher() {
        return valueMatcher;
    }
    /**
     * Sets the value matcher.
     * @param valueMatcher value matcher
     */
    public void setValueMatcher(TextMatcher valueMatcher) {
        this.valueMatcher.copyFrom(valueMatcher);
    }

    @Override
    public OnMatch getOnMatch() {
        return onMatch;
    }
    public void setOnMatch(OnMatch onMatch) {
        this.onMatch = onMatch;
    }

    @Override
    public boolean acceptReference(String reference) {
        var isInclude = getOnMatch() == OnMatch.INCLUDE;
        if (StringUtils.isBlank(valueMatcher.getPattern())) {
            return isInclude;
        }
        var matches = valueMatcher.matches(reference);
        return matches == isInclude;
    }

    @Override
    public void loadFromXML(XML xml) {
        setOnMatch(xml.getEnum("@onMatch", OnMatch.class, onMatch));
        valueMatcher.loadFromXML(xml.getXML("valueMatcher"));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("onMatch", onMatch);
        valueMatcher.saveToXML(xml.addElement("valueMatcher"));
    }

    @Override
    public boolean acceptDocument(Doc document) {
        return acceptReference(document.getReference());
    }
    @Override
    public boolean acceptMetadata(String reference, Properties metadata) {
        return acceptReference(reference);
    }
}

