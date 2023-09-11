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
package com.norconex.committer.core.batch.queue;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.batch.BatchConsumer;
import com.norconex.committer.core.batch.queue.impl.FSQueue;

/**
 * A committer queue, accumulating requests before they are committed
 * in one or multiple batches.
 */
@JsonDeserialize(as=FSQueue.class)
public interface CommitterQueue extends AutoCloseable {

    //MAYBE: have abstract committer queue that takes care of initialization?
    /**
     * Initializes the committer queue.
     * @param committerContext committer context
     * @param batchConsumer batch consumer
     * @throws CommitterQueueException could not initialize committer queue
     */
    void init(CommitterContext committerContext, BatchConsumer batchConsumer)
            throws CommitterQueueException;

    //MAYBE: Return new queue size after this queue request?
    /**
     * Queues a committer request to be later processed in batch.
     * @param request committer request
     * @throws CommitterQueueException could not queue committer request
     */
    void queue(CommitterRequest request) throws CommitterQueueException;

    /**
     * Cleans any persisted information specific to this queue.
     * @throws CommitterQueueException could not clean queue
     */
    void clean() throws CommitterQueueException;

    @Override
    void close() throws CommitterQueueException;
}
