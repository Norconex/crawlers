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
import java.io.Serializable;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.filter.impl.RegexMetadataFilter;

/**
 * <p>Base class for handlers applying only to certain type of documents
 * by providing a way to restrict applicable documents based on 
 * a metadata value (matched via regex).
 * </p>
 * <p>Subclasses implementing {@link IXMLConfigurable} should allow this inner 
 * configuration:</p>
 * <pre>
 *  &lt;restrictTo
 *          caseSensitive="[false|true]" &gt;
 *          property="(name of header/metadata name to match)"
 *      (regular expression of value to match)
 *  &lt;/restrictTo&gt;
 * </pre>
 * <p>
 * Subclasses must test if a document is accepted using the 
 * {@link #documentAccepted(Properties, boolean)} method.
 * </p>
 * <p>
 * Subclasses can safely be used as either pre-parse or post-parse handlers.
 * </p>
 * @author Pascal Essiembre
 */
public abstract class AbstractRestrictiveHandler implements Serializable {

    private static final long serialVersionUID = 2115842279928499496L;
    private static final Logger LOG = 
            LogManager.getLogger(AbstractRestrictiveHandler.class);
    
    private final RegexMetadataFilter filter = new RegexMetadataFilter();

    public void setRestriction(
            String metaProperty, String regex, boolean caseSensitive) {
        filter.setProperty(metaProperty);
        filter.setRegex(regex);
        filter.setCaseSensitive(caseSensitive);
    }

    protected boolean documentAccepted(
            String reference, Properties metadata, boolean parsed)
            throws IOException {
        if (filter.acceptDocument(null, metadata, parsed)) {
            return true;
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Document filtered out by import handler: " + reference);
        }
        return false;
    }

    /**
     * Convenience method for subclasses to load metadata property restrictions.
     * @param xml xml configuration
     */
    protected void loadFromXML(XMLConfiguration xml) {
        filter.setProperty(xml.getString("restrictTo[@property]"));
        filter.setCaseSensitive(
                xml.getBoolean("restrictTo[@caseSensitive]", false));
        filter.setRegex(xml.getString("restrictTo", null));
    }
    
    /**
     * Convenience method for subclasses to save metadata restrictions.
     * @param writer XML writer
     * @throws XMLStreamException problem saving extra content types
     */
    protected void saveToXML(XMLStreamWriter writer) throws XMLStreamException {
        writer.writeStartElement("restrictTo");
        if (filter.getProperty() != null) {
            writer.writeAttribute("property", filter.getProperty());
        }
        writer.writeAttribute("caseSensitive", 
                Boolean.toString(filter.isCaseSensitive()));
        if (filter.getRegex() != null) {
            writer.writeCharacters(filter.getRegex());
        }
        writer.writeEndElement();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((filter == null) ? 0 : filter.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AbstractRestrictiveHandler other = (AbstractRestrictiveHandler) obj;
        if (filter == null) {
            if (other.filter != null) {
                return false;
            }
        } else if (!filter.equals(other.filter)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.DEFAULT_STYLE)
                    .append("filter", filter).toString();
    }
}
