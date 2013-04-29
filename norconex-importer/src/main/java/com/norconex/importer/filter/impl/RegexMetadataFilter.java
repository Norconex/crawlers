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
package com.norconex.importer.filter.impl;

import java.io.IOException;
import java.io.InputStream;
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

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.filter.AbstractOnMatchFilter;
import com.norconex.importer.filter.IDocumentFilter;
import com.norconex.importer.filter.OnMatch;
/**
 * Accepts or rejects a document based on its property values using 
 * regular expression.  
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;filter class="com.norconex.importer.filter.RegexMetadataFilter"
 *          onMatch="[include|exclude]" 
 *          caseSensitive="[false|true]" &gt;
 *          property="(name of metadata name to match)"
 *      (regular expression of value to match)
 *  &lt;/filter&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class RegexMetadataFilter extends AbstractOnMatchFilter
        implements IDocumentFilter, IXMLConfigurable {

    private static final long serialVersionUID = -8029862304058855686L;

    private boolean caseSensitive;
    private String property;
    private String regex;
    private Pattern pattern;

    public RegexMetadataFilter() {
        this(null, null, OnMatch.INCLUDE);
    }
    public RegexMetadataFilter(String header, String regex) {
        this(header, regex, OnMatch.INCLUDE);
    }
    public RegexMetadataFilter(String header, String regex, OnMatch onMatch) {
        this(header, regex, onMatch, false);
    }
    public RegexMetadataFilter(
            String header, String regex, 
            OnMatch onMatch, boolean caseSensitive) {
        super();
        setCaseSensitive(caseSensitive);
        setProperty(header);
        setOnMatch(onMatch);
        setRegex(regex);
    }
    
    public String getRegex() {
        return regex;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    public String getProperty() {
        return property;
    }
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    public void setProperty(String property) {
        this.property = property;
    }
    public void setRegex(String regex) {
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
    public final boolean acceptDocument(
            InputStream document, Properties metadata, boolean parsed)
            throws IOException {
        if (StringUtils.isBlank(regex)) {
            return getOnMatch() == OnMatch.INCLUDE;
        }
        Collection<String> values =  metadata.getStrings(property);
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
        setProperty(xml.getString("[@property]"));
        setRegex(xml.getString(""));
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
        super.loadFromXML(xml);

    }
    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("filter");
            writer.writeAttribute("class", getClass().getCanonicalName());
            super.saveToXML(writer);
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
        builder.append("RegexMetadataFilter [property=")
                .append(property)
                .append(", regex=").append(regex)
                .append(", caseSensitive=").append(caseSensitive)
                .append(", onMatch=").append(getOnMatch()).append("]");
        return builder.toString();
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (caseSensitive ? 1231 : 1237);
        result = prime * result + ((property == null) ? 0 : property.hashCode());
        result = prime * result + ((regex == null) ? 0 : regex.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        RegexMetadataFilter other = (RegexMetadataFilter) obj;
        if (caseSensitive != other.caseSensitive)
            return false;
        if (property == null) {
            if (other.property != null)
                return false;
        } else if (!property.equals(other.property))
            return false;
        if (regex == null) {
            if (other.regex != null)
                return false;
        } else if (!regex.equals(other.regex))
            return false;
        return true;
    }
    
}

