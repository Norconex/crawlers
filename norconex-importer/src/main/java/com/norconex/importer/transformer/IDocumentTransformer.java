package com.norconex.importer.transformer;

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.Writer;

import com.norconex.commons.lang.map.Properties;

/**
 * Transformers allow to manipulate and convert extracted text and
 * save the modified text back.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDocumentTransformer extends Serializable {

    /**
     * Transforms document content and metadata.
     * @param reference document reference (e.g. URL)
     * @param input document to transform
     * @param output transformed document
     * @param metadata document metadata
     */
    void transformDocument(
            String reference, Reader input, Writer output, Properties metadata)
            throws IOException;
}
