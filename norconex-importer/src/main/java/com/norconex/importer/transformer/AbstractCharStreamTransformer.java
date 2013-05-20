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
package com.norconex.importer.transformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Pattern;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.Importer;

/**
 * <p>Base class for transformers dealing with text documents only.  Subclasses
 * can safely be used as either pre-parse or post-parse handlers.
 * </p>
 * <p>
 * For pre-parsing, non-text documents will simply be ignored and no
 * transformation will occur.  To find out if a document is a text-one, the
 * metadata {@link Importer#DOC_CONTENT_TYPE} value is used. By default
 * any content type starting with "text/" is considered text.  This default
 * behavior can be changed with the {@link #setContentTypeRegex(String)} method.
 * One must make sure to only match text documents to parsing exceptions.
 * </p>
 * <p>
 * For post-parsing, all documents are assumed to be text.
 * </p>
 * <p>
 * Sub-classes can restrict to which document to apply this transformation
 * based on document metadata (see {@link AbstractRestrictiveTransformer}).
 * </p>
 * <p>
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
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public abstract class AbstractCharStreamTransformer 
            extends AbstractRestrictiveTransformer {

    private static final long serialVersionUID = -7465364282740091371L;
    private static final Logger LOG = 
            LogManager.getLogger(AbstractCharStreamTransformer.class);
    
    private Pattern contentTypeRegex = Pattern.compile("^text/.*$");
    
    
    public String getContentTypeRegex() {
        return contentTypeRegex.toString();
    }
    public void setContentTypeRegex(String contentTypeRegex) {
        this.contentTypeRegex = Pattern.compile(contentTypeRegex);
    }
    
    @Override
    protected final void transformRestrictedDocument(
            String reference, InputStream input,
            OutputStream output, Properties metadata, boolean parsed)
            throws IOException {
        String type = metadata.getString(Importer.DOC_CONTENT_TYPE);
        if (parsed || contentTypeRegex == null 
                || contentTypeRegex.matcher(type).matches()) {
            InputStreamReader is = new InputStreamReader(input);
            OutputStreamWriter os = new OutputStreamWriter(output);
            transformTextDocument(reference, is, os, metadata, parsed);
            os.flush();
        } else if (LOG.isDebugEnabled()) {
            LOG.debug("Content-type \"" + type + "\" does not represent a "
                    + "text file for: " + reference);
        }
    }

    protected abstract void transformTextDocument(
            String reference, Reader input,
            Writer output, Properties metadata, boolean parsed)
            throws IOException;
    
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
        return "AbstractTextTransformer [contentTypeRegex=" + contentTypeRegex
                + "]";
    }
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime
                * result
                + ((contentTypeRegex == null) ? 0 : contentTypeRegex.hashCode());
        return result;
    }
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!super.equals(obj)) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        AbstractCharStreamTransformer other = (AbstractCharStreamTransformer) obj;
        if (contentTypeRegex == null) {
            if (other.contentTypeRegex != null) {
                return false;
            }
        } else if (!contentTypeRegex.equals(other.contentTypeRegex)) {
            return false;
        }
        return true;
    }
}