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

/**
 * Base class wrapping Apache Tika parser for use by the importer.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public class AbstractTikaParser implements IDocumentParser {

    private static final long serialVersionUID = -6183461314335335495L;

    private final Parser parser;
    private final String format;

    /**
     * Creates a new Tika-based parser.
     * @param parser Tika parser
     * @param format one of Tika parser supported format
     */
    public AbstractTikaParser(Parser parser, String format) {
        super();
        this.parser = parser;
        this.format = format;
    }

    @Override
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

//        ContentHandler bodyhandler = new BodyContentHandler();
        
        TransformerHandler handler;
        try {
            handler = factory.newTransformerHandler();
            handler.getTransformer().setOutputProperty(
                    OutputKeys.METHOD, format);
            handler.getTransformer().setOutputProperty(
                    OutputKeys.INDENT, "yes");
            
//            ByteArrayOutputStream os = new ByteArrayOutputStream(50000);
            
//            handler.setResult(new StreamResult(os));
            handler.setResult(new StreamResult(output));
            
            Parser parser = new RecursiveMetadataParser(
                    this.parser, output, metadata);
            ParseContext context = new ParseContext();
            context.set(Parser.class, parser);

            parser.parse(inputStream, handler, tikaMetadata, context);
            
//            System.err.println("STRING:" + os.toString());
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

            //TODO Make it a file writer somehow?? storing it as new document
            // so we can have a zip and its containing files separate.
            ContentHandler content = new BodyContentHandler(writer);
            super.parse(stream, content, tikaMeta, context);
//            super.parse(stream, handler, tikaMeta, context);
            addTikaMetadata(tikaMeta, metadata);
        }
    }
}
