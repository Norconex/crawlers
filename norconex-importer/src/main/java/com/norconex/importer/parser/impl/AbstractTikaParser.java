package com.norconex.importer.parser.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.ContentType;
import com.norconex.importer.Importer;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.IDocumentParser;

public class AbstractTikaParser implements IDocumentParser {

    private static final long serialVersionUID = -6183461314335335495L;

    //TODO need a way to recreate (such as a "create" method) since
    //it is not serializable, and failing in some frameworks (e.g. Wicket).
    private transient final Parser parser;
    private final String format;

    public AbstractTikaParser(Parser parser, String format) {
        super();
        this.parser = parser;
        this.format = format;
    }

    public final void parseDocument(
            InputStream inputStream, ContentType contentType,
            Writer output, Properties metadata)
            throws DocumentParserException {

        org.apache.tika.metadata.Metadata tikaMetadata = 
                new org.apache.tika.metadata.Metadata();
        tikaMetadata.set(org.apache.tika.metadata.Metadata.CONTENT_TYPE, 
                contentType.toString());
        tikaMetadata.set(org.apache.tika.metadata.Metadata.RESOURCE_NAME_KEY, 
                metadata.getString(Importer.DOC_REFERENCE));
        SAXTransformerFactory factory = (SAXTransformerFactory)
        SAXTransformerFactory.newInstance();
        TransformerHandler handler;
        try {
            handler = factory.newTransformerHandler();
            handler.getTransformer().setOutputProperty(
                    OutputKeys.METHOD, format);
            handler.getTransformer().setOutputProperty(
                    OutputKeys.INDENT, "yes");
            handler.setResult(new StreamResult(output));
            
            Parser parser = new RecursiveMetadataParser(
                    this.parser, output, metadata);
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);

            parser.parse(inputStream, handler, tikaMetadata, context);
        } catch (Exception e) {
            throw new DocumentParserException(e);
        }
    }
    
    protected void addTikaMetadata(
            org.apache.tika.metadata.Metadata tikaMeta, Properties metadata) {
        String[]  names = tikaMeta.names();
        for (int i = 0; i < names.length; i++) {
            String name = names[i];
            metadata.addString(name, tikaMeta.getValues(name));
        }
    }
    
    protected class RecursiveMetadataParser extends ParserDecorator {
        private static final long serialVersionUID = -5011890258694908887L;
        private final Writer writer;
        private final Properties metadata;
        public RecursiveMetadataParser(
                Parser parser, Writer writer, Properties metadata) {
            super(parser);
            this.writer = writer;
            this.metadata = metadata;
        }
        @Override
        public void parse(InputStream stream, ContentHandler handler,
                org.apache.tika.metadata.Metadata tikaMeta, 
                ParseContext context)
                throws IOException, SAXException, TikaException {

            //TODO Make it a file writer somehow... storing it as new documetn
            // so we can have a zip and its containing files separate.
            ContentHandler content = new BodyContentHandler(writer);
            super.parse(stream, content, tikaMeta, context);
            addTikaMetadata(tikaMeta, metadata);
        }
    }
}
