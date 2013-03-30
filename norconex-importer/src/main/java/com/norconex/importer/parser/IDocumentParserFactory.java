package com.norconex.importer.parser;

import java.io.Serializable;

import com.norconex.importer.ContentType;

/**
 * Factory providing document parsers for documents.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDocumentParserFactory extends Serializable{

    /**
     * Gets a document parser, optionally based on its reference or content
     * type.
     * @param documentReference document reference
     * @param contentType content type
     * @return document parser
     */
    IDocumentParser getParser(
            String documentReference, ContentType contentType);
}
