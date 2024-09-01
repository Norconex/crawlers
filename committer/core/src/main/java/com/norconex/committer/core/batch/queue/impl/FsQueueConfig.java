/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.committer.core.batch.queue.impl;

import com.norconex.committer.core.batch.queue.impl.FsQueue.SplitBatch;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class FsQueueConfig {

    public static final int DEFAULT_BATCH_SIZE = 20;
    public static final int DEFAULT_MAX_PER_FOLDER = 500;

    /**
     * The number of documents to be queued in a batch on disk before
     * consuming that batch. Default
     * is {@value FsQueueConfig#DEFAULT_BATCH_SIZE}.
     */
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * The maximum number of files to be queued on disk in a given folders.
     * A batch size can sometimes be too big for some file systems to handle
     * efficiently.  Having this number lower than the batch size allows
     * to have large batches without having too many files in a single
     * directory. Default
     * is {@value FsQueueConfig#DEFAULT_MAX_PER_FOLDER}.
     */
    private int maxPerFolder = DEFAULT_MAX_PER_FOLDER;

    /**
     * During initialization, whether to attempt committing any leftover
     * files in the committer queue from a previous crawl session.
     * Leftovers are typically associated with an abnormal termination
     * (E.g., prematurely ended).
     */
    private boolean commitLeftoversOnInit = false;

    /**
     * Establishes how to handle commit failures.
     */
    private final OnCommitFailure onCommitFailure = new OnCommitFailure();

    @Data
    @Accessors(chain = true)
    public static class OnCommitFailure {
        /**
         * Whether and how to split batches when re-attempting them
         * when they fail See class documentation for details.
         */
        private SplitBatch splitBatch = SplitBatch.OFF;

        /**
         * Maximum retries upon commit failures. Default is 0 (does not retry).
         */
        private int maxRetries;

        /**
         * Delay in milliseconds between retries. Default is 0 (does not wait).
         */
        private long retryDelay;

        /**
         * Whether to ignore non-critical errors in an attempt to keep going.
         * See class documentation for more details.
         */
        private boolean ignoreErrors;
    }
}
