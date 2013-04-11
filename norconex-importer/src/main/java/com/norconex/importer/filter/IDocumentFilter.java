package com.norconex.importer.filter;

import java.io.IOException;
import java.io.InputStream;

import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.IImportHandler;

/**
 * Filters documents.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDocumentFilter extends IImportHandler {

    /**
     * Whether to accepts a document.
     * @param document the document to evaluate
     * @param metadata document metadata
     * @param parsed whether the document has been parsed already or not (a 
     *        parsed document should normally be text-based)
     * @return <code>true</code> if document is accepted
     * @throws IOException problem reading the document
     */
    boolean acceptDocument(
            InputStream document, Properties metadata, boolean parsed)
        throws IOException;
}
