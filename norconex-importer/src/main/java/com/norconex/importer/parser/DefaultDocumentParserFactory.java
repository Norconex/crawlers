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
 * @author Pascal Essiembre
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
    
    public DefaultDocumentParserFactory() {
        this(DEFAULT_FORMAT);
    }
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
    protected void registerNamedParsers() {
        registerNamedParser(ContentType.HTML, new HTMLParser(format));
        registerNamedParser(ContentType.PDF, new PDFParser(format));
        registerNamedParser(ContentType.XPDF, new PDFParser(format));
    }
    protected void registerFallbackParser() {
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
