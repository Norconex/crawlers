package com.norconex.importer.filter;

import java.io.IOException;
import java.io.Reader;

import com.norconex.commons.lang.map.Properties;

/**
 * Filters documents.
 * @author <a href="mailto:pascal.essiembre@norconex.com">Pascal Essiembre</a>
 */
public interface IDocumentFilter {

    /**
     * Whether to accepts a document.
     * @param document the document to evaluate
     * @param metadata document metadata
     * @return <code>true</code> if document is accepted
     * @throws IOException problem reading the document
     */
    boolean acceptDocument(Reader document, Properties metadata)
        throws IOException;
}
