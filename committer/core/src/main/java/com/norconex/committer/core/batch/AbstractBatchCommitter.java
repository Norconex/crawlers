/* Copyright 2020-2022 Norconex Inc.
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

import com.norconex.committer.core.AbstractCommitter;
import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.queue.CommitterQueue;
import com.norconex.committer.core.batch.queue.impl.FSQueue;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;

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
 * The default queue is {@link FSQueue} (file-system queue).
 * </p>
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#restrictTo}
 *
 * {@nx.include com.norconex.committer.core.AbstractCommitter#fieldMappings}
 *
 * <p>Subclasses inherits this {@link XMLConfigurable} configuration:</p>
 *
 * {@nx.xml #options
 *   {@nx.include com.norconex.committer.core.AbstractCommitter@nx.xml.usage}
 *
 *   <!-- Settings for default queue implementation ("class" is optional): -->
 *   {@nx.include com.norconex.committer.core.batch.queue.impl.FSQueue@nx.xml.usage}
 * }
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public abstract class AbstractBatchCommitter extends AbstractCommitter
        implements XMLConfigurable, BatchConsumer {

    private CommitterQueue queue = new FSQueue();

    @Override
    protected final void doInit() throws CommitterException {
        if (queue == null) {
            queue = new FSQueue();
        }
        initBatchCommitter();
        queue.init(getCommitterContext(), this);
    }
    @Override
    protected void doUpsert(UpsertRequest upsertRequest)
            throws CommitterException {
        queue.queue(upsertRequest);
    }
    @Override
    protected void doDelete(DeleteRequest deleteRequest)
            throws CommitterException {
        queue.queue(deleteRequest);
    }
    @Override
    protected void doClose() throws CommitterException {
        try {
            queue.close();
        } finally {
            closeBatchCommitter();
        }
    }
    @Override
    protected void doClean() throws CommitterException {
        queue.clean();
    }

    @Override
    public void consume(Iterator<CommitterRequest> it)
            throws CommitterException {
        fireInfo(CommitterEvent.COMMITTER_BATCH_BEGIN);
        try {
            commitBatch(it);
        } catch (CommitterException | RuntimeException e) {
            fireError(CommitterEvent.COMMITTER_BATCH_ERROR, e);
            throw  e;
        }
        fireInfo(CommitterEvent.COMMITTER_BATCH_END);
    }

    @Override
    public final void loadCommitterFromXML(XML xml) {
        loadBatchCommitterFromXML(xml);
        setCommitterQueue(
                xml.getObjectImpl(CommitterQueue.class, "queue", queue));
    }
    @Override
    public final void saveCommitterToXML(XML xml) {
        saveBatchCommitterToXML(xml);
        xml.addElement("queue", queue);
    }

    protected abstract void loadBatchCommitterFromXML(XML xml);
    protected abstract void saveBatchCommitterToXML(XML xml);

    public CommitterQueue getCommitterQueue() {
        return queue;
    }
    public void setCommitterQueue(CommitterQueue queue) {
        this.queue = queue;
    }

    /**
     * Subclasses can perform additional initialization by overriding this
     * method. Default implementation does nothing. The committer context
     * and committer queue will be already initialized when invoking
     * {@link #getCommitterContext()} and {@link #getCommitterQueue()},
     * respectively.
     * @throws CommitterException error initializing
     */
    protected void initBatchCommitter()
            throws CommitterException {
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
        //NOOP
    }
}