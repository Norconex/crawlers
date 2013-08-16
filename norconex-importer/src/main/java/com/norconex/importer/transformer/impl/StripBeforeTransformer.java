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
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.transformer.AbstractStringTransformer;

/**
 * <p>Strips any content found before first match found for given pattern.</p>
 * 
 * <p>This class can be used as a pre-parsing (text content-types only) 
 * or post-parsing handlers.</p>
 * <p>
 * XML configuration usage:
 * </p>
 * <pre>
 *  &lt;transformer class="com.norconex.importer.transformer.impl.StripBeforeTransformer"
 *          inclusive="[false|true]" 
 *          caseSensitive="[false|true]" &gt;
 *      &lt;contentTypeRegex&gt;
 *          (regex to identify text content-types for pre-import, 
 *           overriding default)
 *      &lt;/contentTypeRegex&gt;
 *      &lt;restrictTo
 *              caseSensitive="[false|true]" &gt;
 *              property="(name of header/metadata name to match)"
 *          (regular expression of value to match)
 *      &lt;/restrictTo&gt;
 *      &lt;stripBeforeRegex&gt(regex)&lt;/stripBeforeRegex&gt
 *  &lt;/transformer&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class StripBeforeTransformer extends AbstractStringTransformer
        implements IXMLConfigurable {

    private static final long serialVersionUID = 1755678509630926102L;

    private static final Logger LOG = 
            LogManager.getLogger(StripBeforeTransformer.class);    

    private boolean inclusive;
    private boolean caseSensitive;
    private String stripBeforeRegex;

    @Override
    protected void transformStringDocument(String reference,
            StringBuilder content, Properties metadata, boolean parsed,
            boolean partialContent) {
        if (stripBeforeRegex == null) {
            LOG.error("No regular expression provided.");
            return;
        }
        int flags = Pattern.DOTALL | Pattern.UNICODE_CASE;
        if (!caseSensitive) {
            flags = flags | Pattern.CASE_INSENSITIVE;
        }
        Pattern pattern = Pattern.compile(stripBeforeRegex, flags);
        Matcher match = pattern.matcher(content);
        if (match.find()) {
            if (inclusive) {
                content.delete(0, match.end());
            } else {
                content.delete(0, match.start());
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

    public String getStripBeforeRegex() {
        return stripBeforeRegex;
    }
    public void setStripBeforeRegex(String regex) {
        this.stripBeforeRegex = regex;
    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        XMLConfiguration xml = ConfigurationLoader.loadXML(in);
        setCaseSensitive(xml.getBoolean("[@caseSensitive]", false));
        setInclusive(xml.getBoolean("[@inclusive]", false));
        super.loadFromXML(xml);
        setStripBeforeRegex(xml.getString("stripBeforeRegex", null));
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
            writer.writeStartElement("stripBeforeRegex");
            writer.writeCharacters(stripBeforeRegex);
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
        return new ToStringBuilder(this)
            .appendSuper(super.toString())
            .append("inclusive", inclusive)
            .append("caseSensitive", caseSensitive)
            .append("stripBeforeRegex", stripBeforeRegex)
            .toString();
    }
    
    @Override
    public int hashCode() {
        return new HashCodeBuilder()
            .appendSuper(super.hashCode())
            .append(caseSensitive)
            .append(inclusive)
            .append(stripBeforeRegex)
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
        if (!(obj instanceof StripBeforeTransformer)) {
            return false;
        }
        StripBeforeTransformer other = (StripBeforeTransformer) obj;
        return new EqualsBuilder()
            .appendSuper(super.equals(obj))
            .append(caseSensitive, other.caseSensitive)
            .append(inclusive, other.inclusive)
            .append(stripBeforeRegex, other.stripBeforeRegex)
            .isEquals();
    }

}
