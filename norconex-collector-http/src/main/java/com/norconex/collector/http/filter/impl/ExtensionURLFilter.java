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

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.filter.AbstractOnMatchFilter;
import com.norconex.collector.http.filter.IHttpDocumentFilter;
import com.norconex.collector.http.filter.IHttpHeadersFilter;
import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.filter.OnMatch;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * Filters URL based on coma-separated list of file extensions.
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;filter class="com.norconex.collector.http.filter.impl.ExtensionURLFilter"
 *          onMatch="[include|exclude]" 
 *          caseSensitive="[false|true]" &gt;
 *      (comma-separated list of extensions)
 *  &lt;/filter&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
@SuppressWarnings("nls")
public class ExtensionURLFilter extends AbstractOnMatchFilter implements 
        IURLFilter, 
        IHttpDocumentFilter, 
        IHttpHeadersFilter, 
        IXMLConfigurable {

    private static final long serialVersionUID = -8029862304058855686L;

    private boolean caseSensitive;
    private String extensions;
    private String[] extensionParts;

    public ExtensionURLFilter() {
        this(null, OnMatch.INCLUDE, false);
    }
    public ExtensionURLFilter(String extensions) {
        this(extensions, OnMatch.INCLUDE, false);
    }
    public ExtensionURLFilter(String extensions, OnMatch onMatch) {
        this(extensions, onMatch, false);
    }
    public ExtensionURLFilter(
            String extensions, OnMatch onMatch, boolean caseSensitive) {
        super();
        setExtensions(extensions);
        setOnMatch(onMatch);
        setCaseSensitive(caseSensitive);
    }

    @Override
    public boolean acceptURL(String url) {
        if (StringUtils.isBlank(extensions)) {
            return getOnMatch() == OnMatch.INCLUDE;
        }
        String urlExtension = url.replaceFirst("(.*\\.)(.*?)", "$2");
        for (int i = 0; i < extensionParts.length; i++) {
            String ext = extensionParts[i];
            if (!isCaseSensitive() && ext.equalsIgnoreCase(urlExtension)) {
                return getOnMatch() == OnMatch.INCLUDE;
            } else if (isCaseSensitive() && ext.equals(urlExtension)) {
                return getOnMatch() == OnMatch.INCLUDE;
            }
        }
        return getOnMatch() == OnMatch.EXCLUDE;
    }

    
    /**
     * @return the extensions
     */
    public String getExtensions() {
        return extensions;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }
    public void setExtensions(String extensions) {
        this.extensions = extensions;
        if (extensions != null) {
            this.extensionParts = extensions.split(",");
        } else {
            this.extensionParts = ArrayUtils.EMPTY_STRING_ARRAY;
        }
    }
    @Override
    public void loadFromXML(Reader in)  {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setExtensions(xml.getString(""));
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
            writer.writeCharacters(extensions);
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
        builder.append("ExtensionURLFilter [extensions=")
                .append(extensions)
                .append(", caseSensitive=").append(caseSensitive)
                .append(", onMatch=").append(getOnMatch()).append("]");
        return builder.toString();
    }
    @Override
    public boolean acceptDocument(HttpDocument document) {
        return acceptURL(document.getUrl());
    }
    @Override
    public boolean acceptHeaders(String url, Properties headers) {
        return acceptURL(url);
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (caseSensitive ? 1231 : 1237);
        result = prime * result
                + ((extensions == null) ? 0 : extensions.hashCode());
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
        ExtensionURLFilter other = (ExtensionURLFilter) obj;
        if (caseSensitive != other.caseSensitive)
            return false;
        if (extensions == null) {
            if (other.extensions != null)
                return false;
        } else if (!extensions.equals(other.extensions))
            return false;
        return true;
    }

}
