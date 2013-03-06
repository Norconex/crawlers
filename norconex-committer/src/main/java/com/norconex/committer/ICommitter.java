package com.norconex.committer;

import java.io.File;
import java.io.Serializable;

import com.norconex.commons.lang.meta.Metadata;

/**
 * Commits documents to their final destination (e.g. search engine).
 * @author Pascal Essiembre
 */
public interface ICommitter extends Serializable {

    /**
     * Queues a new or modified document.   These queued documents should
     * be sent to their target destination when commit is called.
     * @param reference document reference (e.g. URL)
     * @param document text document 
     * @param metadata document metadata
     */
    void queueAdd(String reference, File document, Metadata metadata);    

    /**
     * Queues a document for removal.   These queued documents should
     * be sent to their target destination for deletion when commit is called.
     * @param reference document reference (e.g. URL)
     * @param document text document 
     * @param metadata document metadata
     */
    void queueRemove(String reference, File document, Metadata metadata);    

    /**
     * Commits queued documents.  Effectively apply the additions and removals.
     */
    void commit();
}
