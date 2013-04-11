package com.norconex.importer.tagger;

import java.io.IOException;
import java.io.InputStream;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.IImportHandler;

/**
 * Tags a document with extra metadata information, or manipulate existing
 * metadata information.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDocumentTagger extends IImportHandler {

    /**
     * Tags a document with extra metadata information.
     * @param reference document reference (e.g. URL)
     * @param document document
     * @param metadata document metadata
     * @param parsed whether the document has been parsed already or not (a 
     *        parsed document should normally be text-based)
     * @throws IOException problem reading the document
     */
    void tagDocument(String reference, InputStream document, 
            Properties metadata, boolean parsed)
            throws IOException;
}
