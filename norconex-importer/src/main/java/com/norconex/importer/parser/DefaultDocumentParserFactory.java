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
package com.norconex.importer.parser;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.apache.commons.configuration.XMLConfiguration;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.importer.ContentType;
import com.norconex.importer.parser.impl.FallbackParser;
import com.norconex.importer.parser.impl.HTMLParser;
import com.norconex.importer.parser.impl.PDFParser;

/**
 * Uses Apacke Tika for all its supported content types.  For unknown
 * content types, falls back to Tika generic media detector/parser.
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;documentParserFactory class="com.norconex.importer.parser.DefaultDocumentParserFactory" format="text|xml" /&gt;
 * </pre>
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
@SuppressWarnings("nls")
public class DefaultDocumentParserFactory 
        implements IDocumentParserFactory, 
                   IXMLConfigurable {

    private static final long serialVersionUID = 6639928288252330105L;
    
    public static final String DEFAULT_FORMAT = "text";
    
    private final Map<ContentType, IDocumentParser> namedParsers = 
            new HashMap<ContentType, IDocumentParser>();
    private IDocumentParser fallbackParser;
    private String format;
    
    /**
     * Creates a new document parser factory of "text" format.
     */
    public DefaultDocumentParserFactory() {
        this(DEFAULT_FORMAT);
    }
    /**
     * Creates a new document parser factory of the given format.
     * @param format dependent on parser expectations but typically, one 
     *        of "text" or "xml"
     */
    public DefaultDocumentParserFactory(String format) {
        super();
        this.format = format;
        registerNamedParsers();
        registerFallbackParser();
    }

    /**
     * Gets a parser based on content type, regardless of document reference
     * (ignoring it).
     */
    @Override
    public final IDocumentParser getParser(
            String documentReference, ContentType contentType) {
        IDocumentParser parser = namedParsers.get(contentType);
        if (parser == null) {
            return fallbackParser;
        }
        return parser;
    }
    
    public String getFormat() {
        return format;
    }
    public void setFormat(String format) {
        this.format = format;
    }
    protected final void registerNamedParser(
            ContentType contentType, IDocumentParser parser) {
        namedParsers.put(contentType, parser);
    }
    protected final void registerFallbackParser(IDocumentParser parser) {
        this.fallbackParser = parser;
    }
    protected final IDocumentParser getFallbackParser() {
        return fallbackParser;
    }

    private void registerNamedParsers() {
        registerNamedParser(ContentType.HTML, new HTMLParser(format));
        registerNamedParser(ContentType.PDF, new PDFParser(format));
        registerNamedParser(ContentType.XPDF, new PDFParser(format));
    }
    private void registerFallbackParser() {
        registerFallbackParser(new FallbackParser(format));
    }
    
    @Override
    public void loadFromXML(Reader in) throws IOException {
        try {
            XMLConfiguration xml = ConfigurationLoader.loadXML(in);
            setFormat(xml.getString("[@format]"));
        } catch (ConfigurationException e) {
            throw new IOException("Cannot load XML.", e);
        }
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        XMLOutputFactory factory = XMLOutputFactory.newInstance();
        try {
            XMLStreamWriter writer = factory.createXMLStreamWriter(out);
            writer.writeStartElement("tagger");
            writer.writeAttribute("class", getClass().getCanonicalName());
            if (format != null) {
                writer.writeAttribute("format", format);
            }
            writer.writeEndElement();
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }
    }
}
