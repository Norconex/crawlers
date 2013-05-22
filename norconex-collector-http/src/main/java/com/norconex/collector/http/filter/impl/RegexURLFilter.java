/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.filter.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.filter.AbstractOnMatchFilter;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.filter.OnMatch;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
/**
 * Filters URL based on a regular expression.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;filter class="com.norconex.collector.http.filter.impl.RegexURLFilter"
 *          onMatch="[include|exclude]" 
 *          caseSensitive="[false|true]" &gt;
 *      (regular expression)
 *  &lt;/filter&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
@SuppressWarnings("nls")
public class RegexURLFilter extends AbstractOnMatchFilter implements 
        IURLFilter, 
        IHttpDocumentFilter,
        IHttpHeadersFilter,
        IXMLConfigurable {

    private static final long serialVersionUID = -8029862304058855686L;

    private boolean caseSensitive;
    private String regex;
    private Pattern pattern;

    public RegexURLFilter() {
        this(null, OnMatch.INCLUDE);
    }
    public RegexURLFilter(String regex) {
        this(regex, OnMatch.INCLUDE);
    }
    public RegexURLFilter(String regex, OnMatch onMatch) {
        this(regex, onMatch, false);
    }
    public RegexURLFilter(
            String regex, OnMatch onMatch, boolean caseSensitive) {
        super();
        setOnMatch(onMatch);
        setCaseSensitive(caseSensitive);
        setRegex(regex);
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
    }
    public final void setRegex(String regex) {
        this.regex = regex;
        if (regex != null) {
            if (caseSensitive) {
                this.pattern = Pattern.compile(regex);
            } else {
                this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            }
        } else {
            this.pattern = Pattern.compile(".*");
        }
    }

    @Override
    public boolean acceptURL(String url) {
        boolean isInclude = getOnMatch() == OnMatch.INCLUDE;  
        if (StringUtils.isBlank(regex)) {
            return isInclude;
        }
        boolean matches = pattern.matcher(url).matches();
        return matches && isInclude || !matches && !isInclude;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setRegex(xml.getString(""));
        setOnMatch(getOnMatch(xml));
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("filter");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttribute("onMatch",
                    getOnMatch().toString().toLowerCase());
            writer.writeAttribute("caseSensitive", 
                    Boolean.toString(caseSensitive));
            writer.writeCharacters(regex == null ? "" : regex);
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
    @Override
    public boolean acceptDocument(HttpDocument document) {
        return acceptURL(document.getUrl());
    }
    @Override
    public boolean acceptDocument(String url, HttpMetadata headers) {
        return acceptURL(url);
    }
    
    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RegexURLFilter [regex=")
                .append(regex)
                .append(", caseSensitive=").append(caseSensitive)
                .append(", onMatch=").append(getOnMatch()).append("]");
        return builder.toString();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(caseSensitive)
            .append(pattern)
            .append(regex)
            .toHashCode();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof RegexURLFilter)) {
            return false;
        }
        RegexURLFilter other = (RegexURLFilter) obj;
        return new EqualsBuilder()
            .append(caseSensitive, other.caseSensitive)
            .append(pattern, other.pattern)
            .append(regex, other.regex)
            .isEquals();
    }
}

