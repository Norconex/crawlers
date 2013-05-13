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
import java.util.Collection;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.filter.AbstractOnMatchFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.OnMatch;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
/**
 * Accepts or rejects one or more HTTP header values using regular expression.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;filter class="com.norconex.collector.http.filter.impl.RegexHeaderFilter"
 *          onMatch="[include|exclude]" 
 *          caseSensitive="[false|true]" &gt;
 *          header="(name of header to match)"
 *      (regular expression of value to match)
 *  &lt;/filter&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class RegexHeaderFilter extends AbstractOnMatchFilter
        implements IHttpHeadersFilter, IXMLConfigurable {

    //TODO use Importer RegexMetadataFilter here?  Catching import exception
    
    private static final long serialVersionUID = -8029862304058855686L;

    private boolean caseSensitive;
    private String header;
    private String regex;
    private Pattern pattern;

    public RegexHeaderFilter() {
        this(null, null, OnMatch.INCLUDE);
    }
    public RegexHeaderFilter(String header, String regex) {
        this(header, regex, OnMatch.INCLUDE);
    }
    public RegexHeaderFilter(String header, String regex, OnMatch onMatch) {
        this(header, regex, onMatch, false);
    }
    public RegexHeaderFilter(
            String header, String regex, 
            OnMatch onMatch, boolean caseSensitive) {
        super();
        setCaseSensitive(caseSensitive);
        setHeader(header);
        setOnMatch(onMatch);
        setRegex(regex);
    }
    
    public String getRegex() {
        return regex;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    public String getHeader() {
        return header;
    }
    public final void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    public final void setHeader(String header) {
        this.header = header;
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
    public boolean acceptDocument(String url, HttpMetadata headers) {
        if (StringUtils.isBlank(regex)) {
            return getOnMatch() == OnMatch.INCLUDE;
        }

        Collection<String> values = headers.getStrings(header);
        for (Object value : values) {
            String strVal = ObjectUtils.toString(value);
            if (pattern.matcher(strVal).matches()) {
                return getOnMatch() == OnMatch.INCLUDE;
            }
        }
        return getOnMatch() == OnMatch.EXCLUDE;
    }

    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setHeader(xml.getString("[@header]"));
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
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("RegexMetadataFilter [header=")
                .append(header)
                .append(", regex=").append(regex)
                .append(", caseSensitive=").append(caseSensitive)
                .append(", onMatch=").append(getOnMatch()).append("]");
        return builder.toString();
    }
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .append(caseSensitive)
            .append(header)
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
        if (!(obj instanceof RegexHeaderFilter)) {
            return false;
        }
        RegexHeaderFilter other = (RegexHeaderFilter) obj;
        return new EqualsBuilder()
            .append(caseSensitive, other.caseSensitive)
            .append(header, other.header)
            .append(regex, other.regex)
            .isEquals();
    }
}

