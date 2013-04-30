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
package com.norconex.importer.transformer.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.transformer.AbstractStringTransformer;

/**
 * <p>Strips any content found after first match found for given pattern.</p>
 * 
 * <p>This class can be used as a pre-parsing (text content-types only) 
 * or post-parsing handlers.</p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;transformer class="com.norconex.importer.transformer.impl.StripAfterTransformer"
 *          inclusive="[false|true]" 
 *          caseSensitive="[false|true]" &gt;
 *      &lt;contentTypeRegex&gt;
 *          (stripAfterRegex to identify text content-types for pre-import, 
 *           overriding default)
 *      &lt;/contentTypeRegex&gt;
 *      &lt;restrictTo
 *              caseSensitive="[false|true]" &gt;
 *              property="(name of header/metadata name to match)"
 *          (regular expression of value to match)
 *      &lt;/restrictTo&gt;
 *      &lt;stripAfterRegex&gt;(regex)&lt;/stripAfterRegex&gt;
 *  &lt;/transformer&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class StripAfterTransformer extends AbstractStringTransformer
        implements IXMLConfigurable {

    private static final long serialVersionUID = 4020624567257802994L;

    private static final Logger LOG = 
            LogManager.getLogger(StripAfterTransformer.class);    

    private boolean inclusive;
    private boolean caseSensitive;
    private String stripAfterRegex;

    @Override
    protected void transformStringDocument(String reference,
            StringBuilder content, Properties metadata, boolean parsed,
            boolean partialContent) {
        if (stripAfterRegex == null) {
            LOG.error("No regular expression provided.");
            return;
        }
        int flags = Pattern.DOTALL | Pattern.UNICODE_CASE;
        if (!caseSensitive) {
            flags = flags | Pattern.CASE_INSENSITIVE;
        }
        Pattern pattern = Pattern.compile(stripAfterRegex, flags);
        Matcher match = pattern.matcher(content);
        if (match.find()) {
            if (inclusive) {
                content.delete(match.start(), content.length());
            } else {
                content.delete(match.end(), content.length());
            }
        }
    }
        
    public boolean isInclusive() {
        return inclusive;
    }
    /**
     * Sets whether start and end text pairs should themselves be stripped or 
     * not.
     * @param inclusive <code>true</code> to strip start and end text
     */
    public void setInclusive(boolean inclusive) {
        this.inclusive = inclusive;
    }
    public boolean isCaseSensitive() {
        return caseSensitive;
    }
    /**
     * Sets whether to ignore case when matching start and end text.
     * @param caseSensitive <code>true</code> to consider character case
     */
    public void setCaseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public String getStripAfterRegex() {
        return stripAfterRegex;
    }
    public void setStripAfterRegex(String regex) {
        this.stripAfterRegex = regex;
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
        setInclusive(xml.getBoolean("[@inclusive]", false));
        super.loadFromXML(xml);
        setStripAfterRegex(xml.getString("stripAfterRegex", null));
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("transformer");
            writer.writeAttribute("class", getClass().getCanonicalName());
            writer.writeAttribute(
                    "caseSensitive", Boolean.toString(isCaseSensitive()));
            writer.writeAttribute("inclusive", Boolean.toString(isInclusive()));
            super.saveToXML(writer);
            writer.writeStartElement("stripAfterRegex");
            writer.writeCharacters(stripAfterRegex);
            writer.writeEndElement();
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }

    @Override
    public String toString() {
        return "StripAfterTransformer [inclusive=" + inclusive
                + ", caseSensitive=" + caseSensitive + ", stripAfterRegex="
                + stripAfterRegex + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + (caseSensitive ? 1231 : 1237);
        result = prime * result + (inclusive ? 1231 : 1237);
        result = prime * result + ((stripAfterRegex == null) 
                ? 0 : stripAfterRegex.hashCode());
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
        StripAfterTransformer other = (StripAfterTransformer) obj;
        if (caseSensitive != other.caseSensitive)
            return false;
        if (inclusive != other.inclusive)
            return false;
        if (stripAfterRegex == null) {
            if (other.stripAfterRegex != null)
                return false;
        } else if (!stripAfterRegex.equals(other.stripAfterRegex))
            return false;
        return true;
    }
}
