package com.norconex.committer;

import java.io.File;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.commons.lang.map.Properties;

/**
 * Base implementation offering to batch the committing of documents.
 * @author Pascal Essiembre
 */
public abstract class BatchableCommitter implements ICommitter {

    private static final long serialVersionUID = 880638478926236689L;
    private static final Logger LOG = LogManager.getLogger(
            BatchableCommitter.class);
    
    public static final int DEFAULT_BATCH_SIZE = 1000;
    
    private int batchSize = DEFAULT_BATCH_SIZE;
    private long docCount;
    
    public BatchableCommitter() {
        super();
    }
    public BatchableCommitter(int batchSize) {
        super();
        this.batchSize = batchSize;
    }
    
    public int getBatchSize() {
        return batchSize;
    }
    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public final void queueAdd(
            String reference, File document, Properties metadata) {
        queueBatchableAdd(reference, document, metadata);
        commitIfReady();
    }
    abstract protected void queueBatchableAdd(
            String reference, File document, Properties metadata);

    public final void queueRemove(
            String ref, File document, Properties metadata) {
        queueBatchableRemove(ref, document, metadata);
        commitIfReady();
    }
    abstract protected void queueBatchableRemove(
            String ref, File document, Properties metadata);
    

    private void commitIfReady() {
        docCount++;
        if (docCount % batchSize == 0) {
            if (LOG.isInfoEnabled()) {
                LOG.info("Batch size reached (" + batchSize 
                        + "). Committing");
            }
            commit();
        }
    }
    
}
