/* Copyright 2014-2022 Norconex Inc.
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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.crawler.core.filter.IDocumentFilter;
import com.norconex.crawler.core.filter.IMetadataFilter;
import com.norconex.crawler.core.filter.IReferenceFilter;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.filter.OnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;
/**
 * <p>
 * Filters URL based on a regular expression.
 * </p>
 *
 * {@nx.xml.usage
 * <filter class="com.norconex.crawler.core.filter.impl.ReferenceFilter"
 *     onMatch="[include|exclude]">
 *   <valueMatcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *     (Expression matching the document reference.)
 *   </valueMatcher>
 * </filter>
 * }
 *
 * {@nx.xml.example
 * <filter class="ReferenceFilter" onMatch="exclude">
 *   <valueMatcher method="regex">.*&#47;login/.*</valueMatcher>
 * </filter>
 * }
 * <p>
 * The above will reject documents having "/login/" in their reference.
 * </p>
 * @see Pattern
 */
@SuppressWarnings("javadoc")
public class ReferenceFilter implements
        OnMatchFilter,
        IReferenceFilter,
        IDocumentFilter,
        IMetadataFilter,
        XMLConfigurable {

    private OnMatch onMatch;
    private final TextMatcher valueMatcher = new TextMatcher();

    public ReferenceFilter() {
        this(null, OnMatch.INCLUDE);
    }
    public ReferenceFilter(TextMatcher valueMatcher) {
        this(valueMatcher, OnMatch.INCLUDE);
    }
    public ReferenceFilter(
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
        boolean isInclude = getOnMatch() == OnMatch.INCLUDE;
        if (StringUtils.isBlank(valueMatcher.getPattern())) {
            return isInclude;
        }
        boolean matches = valueMatcher.matches(reference);
        return matches && isInclude || !matches && !isInclude;
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

    @Override
    public boolean equals(final Object other) {
        return EqualsBuilder.reflectionEquals(this, other);
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE)
                .toString();
    }
}

