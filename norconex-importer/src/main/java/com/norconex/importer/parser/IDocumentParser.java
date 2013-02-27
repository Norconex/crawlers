package com.norconex.importer.parser;

import java.io.InputStream;
import java.io.Serializable;
import java.io.Writer;

import com.norconex.commons.lang.meta.Metadata;
import com.norconex.importer.ContentType;

/**
 * Implementations are responsible for parsing a document (InputStream) to 
 * extract its text and metadata.
 * @author Pascal Essiembre
 */
@SuppressWarnings("nls")
public interface IDocumentParser extends Serializable {

    static final String RDF_BASE_URI = "http://norconex.com/Document";
    static final String RDF_SUBJECT_CONTENT = "Content";

    /**
     * Parses a document.
     * @param inputStream the document to parse
     * @param contentType the content type of the document
     * @param outputStream where to save the extracted text
     * @param metadata where to store the metadata
     * @throws DocumentParserException
     */
    void parseDocument(
            InputStream inputStream, ContentType contentType,
            Writer outputStream, Metadata metadata)
        throws DocumentParserException;
}
