/* Copyright 2020-2024 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.committer.core.batch;

import java.util.Iterator;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.committer.core.AbstractCommitter;
import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.queue.CommitterQueue;
import com.norconex.committer.core.batch.queue.impl.FsQueue;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>
 * A base implementation for doing batch commits. Uses an internal queue
 * for storing update/addition requests and deletion requests.
 * It sends the queued data to the remote target every time a given
 * queue threshold has been reached.  Unless otherwise stated,
 * both additions and deletions count towards that threshold.
 * </p>
 * <p>
 * This class also provides batch-related events:
 * <code>COMMITTER_BATCH_BEGIN</code>,
 * <code>COMMITTER_BATCH_END</code>, and
 * <code>COMMITTER_BATCH_ERROR</code>.
 * </p>
 *
 * <p>
 * The default queue is {@link FsQueue} (file-system queue).
 * </p>
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#fieldMappings}
 *
 * <p>Subclasses inherits this configuration:</p>
 * @param <T> Committer configuration type
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractBatchCommitter<T extends BaseBatchCommitterConfig>
        extends AbstractCommitter<T>
        implements BatchConsumer {

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @JsonIgnore
    private CommitterQueue initializedQueue;

    @Override
    protected final void doInit() throws CommitterException {
        if (getConfiguration().getQueue() != null) {
            initializedQueue = getConfiguration().getQueue();
        } else {
            initializedQueue = new FsQueue();
        }
        initBatchCommitter();
        initializedQueue.init(getCommitterContext(), this);
    }

    @Override
    protected void doUpsert(UpsertRequest upsertRequest)
            throws CommitterException {
        initializedQueue.queue(upsertRequest);
    }

    @Override
    protected void doDelete(DeleteRequest deleteRequest)
            throws CommitterException {
        initializedQueue.queue(deleteRequest);
    }

    @Override
    protected void doClose() throws CommitterException {
        try {
            initializedQueue.close();
        } finally {
            closeBatchCommitter();
        }
    }

    @Override
    protected void doClean() throws CommitterException {
        initializedQueue.clean();
    }

    @Override
    public void consume(Iterator<CommitterRequest> it)
            throws CommitterException {
        fireInfo(CommitterEvent.COMMITTER_BATCH_BEGIN);
        try {
            commitBatch(it);
        } catch (CommitterException | RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_BATCH_ERROR, e);
            throw e;
        }
        fireInfo(CommitterEvent.COMMITTER_BATCH_END);
    }

    protected CommitterQueue getInitializedQueue() {
        return initializedQueue;
    }

    /**
     * Subclasses can perform additional initialization by overriding this
     * method. Default implementation does nothing. The committer context
     * and committer queue will be already initialized when invoking
     * {@link #getCommitterContext()} and {@link #getInitializedQueue()},
     * respectively.
     * @throws CommitterException error initializing
     */
    protected void initBatchCommitter() throws CommitterException {
        //NOOP
    }

    /**
     * Commits the supplied batch.
     * @param it the batch to commit
     * @throws CommitterException could not commit the batch
     */
    protected abstract void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException;

    /**
     * Subclasses can perform additional closing logic by overriding this
     * method. Default implementation does nothing.
     * @throws CommitterException error closing committer
     */
    protected void closeBatchCommitter() throws CommitterException {
        initializedQueue = null;
    }
}