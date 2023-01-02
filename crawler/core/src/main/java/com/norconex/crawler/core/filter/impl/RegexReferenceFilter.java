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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.crawler.core.filter.IDocumentFilter;
import com.norconex.crawler.core.filter.IMetadataFilter;
import com.norconex.crawler.core.filter.IReferenceFilter;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.filter.OnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;
/**
 * <p>
 * Filters URL based on a regular expression.
 * </p>
 * {@nx.xml.usage
 * <filter class="com.norconex.crawler.core.filter.impl.RegexReferenceFilter"
 *     onMatch="[include|exclude]"
 *     caseSensitive="[false|true]">
 *   (regular expression)
 * </filter>
 * }
 *
 * {@nx.xml.example
 * <filter class="com.norconex.crawler.core.filter.impl.RegexReferenceFilter"
 *     onMatch="exclude">
 *   .*&#47;login/.*
 * </filter>
 * }
 * <p>
 * The above will reject documents having "/login/" in their reference.
 * </p>
 * @see Pattern
 * @deprecated Since 2.0.0, use {@link ReferenceFilter}
 */
@Deprecated
public class RegexReferenceFilter implements
        OnMatchFilter,
        IReferenceFilter,
        IDocumentFilter,
        IMetadataFilter,
        XMLConfigurable {

    private static final Logger LOG =
            LoggerFactory.getLogger(RegexReferenceFilter.class);

    private boolean caseSensitive;
    private OnMatch onMatch;
    private String regex;
    private Pattern cachedPattern;

    public RegexReferenceFilter() {
        this(null, OnMatch.INCLUDE);
    }
    public RegexReferenceFilter(String regex) {
        this(regex, OnMatch.INCLUDE);
    }
    public RegexReferenceFilter(String regex, OnMatch onMatch) {
        this(regex, onMatch, false);
    }
    public RegexReferenceFilter(
            String regex, OnMatch onMatch, boolean caseSensitive) {
        super();
        setOnMatch(onMatch);
        setCaseSensitive(caseSensitive);
        setRegex(regex);
    }

    @Override
    public OnMatch getOnMatch() {
        return onMatch;
    }
    public void setOnMatch(OnMatch onMatch) {
        this.onMatch = onMatch;
    }
    /**
     * @return the regex
     */
    public String getRegex() {
        return regex;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    public final void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        cachedPattern = null;
    }
    public final void setRegex(String regex) {
        this.regex = regex;
        cachedPattern = null;
    }

    @Override
    public boolean acceptReference(String url) {
        boolean isInclude = getOnMatch() == OnMatch.INCLUDE;
        if (StringUtils.isBlank(regex)) {
            return isInclude;
        }
        boolean matches = getCachedPattern().matcher(url).matches();
        return matches && isInclude || !matches && !isInclude;
    }

    private synchronized Pattern getCachedPattern() {
        if (cachedPattern != null) {
            return cachedPattern;
        }
        Pattern p;
        if (regex == null) {
            p = Pattern.compile(".*");
        } else {
            int flags = Pattern.DOTALL;
            if (!caseSensitive) {
                flags = flags | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            }
            p = Pattern.compile(regex, flags);
        }
        cachedPattern = p;
        return p;
    }

    @Override
    public void loadFromXML(XML xml) {
        LOG.warn("RegexReferenceFilter is deprecated in favor of "
                + "ReferenceFilter.");
        setOnMatch(xml.getEnum("@onMatch", OnMatch.class, onMatch));
        setCaseSensitive(xml.getBoolean("@caseSensitive", caseSensitive));
        setRegex(xml.getString("."));
        cachedPattern = null;
    }
    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("onMatch", onMatch);
        xml.setAttribute("caseSensitive", caseSensitive);
        xml.setTextContent(regex);
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
        return EqualsBuilder.reflectionEquals(this, other, "cachedPattern");
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this, "cachedPattern");
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE)
                .setExcludeFieldNames("cachedPattern")
                .toString();
    }
}

