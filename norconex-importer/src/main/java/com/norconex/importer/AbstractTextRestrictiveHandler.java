/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex Importer.
 * 
 * Norconex Importer is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex Importer is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex Importer. If not, see <http://www.gnu.org/licenses/>.
 */
package com.norconex.importer;

import java.io.IOException;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;

/**
 * <p>Base class for handlers dealing with text documents only.  Subclasses
 * can safely be used as either pre-parse or post-parse handlers.
 * </p>
 * <p>
 * For pre-parsing, non-text documents will simply be ignored and no
 * transformation will occur.  To find out if a document is a text-one, the
 * metadata {@link Importer#DOC_CONTENT_TYPE} value is used. By default
 * any content type starting with "text/" is considered text.  This default
 * behavior can be changed with the {@link #setContentTypeRegex(String)} method.
 * One must make sure to only match text documents to avoid parsing exceptions.
 * </p>
 * <p>
 * For post-parsing, all documents are assumed to be text.
 * </p>
 * <p>
 * Sub-classes can restrict to which document to apply themselves
 * based on document metadata (see {@link AbstractRestrictiveHandler}).
 * </p>
 * <p>
 * Subclasses must test if a document is accepted using the 
 * {@link #documentAccepted(Properties, boolean)} method.
 * </p>
 * <p>Subclasses implementing {@link IXMLConfigurable} should allow this inner 
 * configuration:</p>
 * <pre>
 *  &lt;contentTypeRegex&gt;
 *      (regex to identify text content-types, overridding default)
 *  &lt;/contentTypeRegex&gt;
 *  &lt;restrictTo
 *          caseSensitive="[false|true]" &gt;
 *          property="(name of header/metadata name to match)"
 *      (regular expression of value to match)
 *  &lt;/restrictTo&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public abstract class AbstractTextRestrictiveHandler 
            extends AbstractRestrictiveHandler {

    private static final long serialVersionUID = -2727051933247903304L;

    private static final Logger LOG = 
            LogManager.getLogger(AbstractTextRestrictiveHandler.class);
    
    private Pattern contentTypeRegex = Pattern.compile("^text/.*$");
    
    
    public String getContentTypeRegex() {
        return contentTypeRegex.toString();
    }
    public void setContentTypeRegex(String contentTypeRegex) {
        this.contentTypeRegex = Pattern.compile(contentTypeRegex);
    }
    
    @Override
    protected boolean documentAccepted(
            String reference, Properties metadata, boolean parsed)
            throws IOException {
        if (!super.documentAccepted(reference, metadata, parsed)) {
            return false;
        }
        String type = metadata.getString(Importer.DOC_CONTENT_TYPE);
        if ( parsed || contentTypeRegex == null 
                || contentTypeRegex.matcher(type).matches()) {
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Content-type \"" + type + "\" does not represent a "
                    + "text file for: " + reference);
        }
        return false;
    }
    
    /**
     * Convenience method for subclasses to load content type regex.
     * (attribute "contentTypeRegex").
     * @param xml xml configuration
     */
    @Override
    protected void loadFromXML(XMLConfiguration xml) {
        setContentTypeRegex(xml.getString(
                "contentTypeRegex", contentTypeRegex.toString()));
        super.loadFromXML(xml);
    }
    
    
    /**
     * Convenience method for subclasses to save content type regex.
     * @param writer XML writer
     * @throws XMLStreamException problem saving 
     */
    @Override
    protected void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("contentTypeRegex");
        if (contentTypeRegex != null) {
            writer.writeCharacters(getContentTypeRegex());
        }
        writer.writeEndElement();
        super.saveToXML(writer);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.DEFAULT_STYLE)
            .appendSuper(super.toString())
            .append("contentTypeRegex", contentTypeRegex)
            .toString();
    }
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(getContentTypeRegex())
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
        if (!(obj instanceof AbstractTextRestrictiveHandler)) {
            return false;
        }
        AbstractTextRestrictiveHandler other = 
                (AbstractTextRestrictiveHandler) obj;
        return new EqualsBuilder()
            .appendSuper(super.equals(obj))
            .append(getContentTypeRegex(), other.getContentTypeRegex())
            .isEquals();
    } 
}