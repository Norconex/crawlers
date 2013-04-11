package com.norconex.importer.transformer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.IImportHandler;

/**
 * Transformers allow to manipulate and convert extracted text and
 * save the modified text back.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDocumentTransformer extends IImportHandler {

    /**
     * Transforms document content and metadata.
     * @param reference document reference (e.g. URL)
     * @param input document to transform
     * @param output transformed document
     * @param metadata document metadata
     * @param parsed whether the document has been parsed already or not (a 
     *        parsed document should normally be text-based)
     */
    void transformDocument(
            String reference, InputStream input, 
            OutputStream output, Properties metadata, boolean parsed)
                    throws IOException;
}
