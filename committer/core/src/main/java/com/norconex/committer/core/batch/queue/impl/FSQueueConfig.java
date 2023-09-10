/* Copyright 2023 Norconex Inc.
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

import com.norconex.committer.core.batch.queue.impl.FSQueue.SplitBatch;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
@SuppressWarnings("javadoc")
public class FSQueueConfig {

    public static final int DEFAULT_BATCH_SIZE = 20;
    public static final int DEFAULT_MAX_PER_FOLDER = 500;

    /**
     * The number of documents to be queued in a batch on disk before
     * consuming that batch.
     * @param batchSize the batch size
     * @return batch size
     */
    private int batchSize = DEFAULT_BATCH_SIZE;

    /**
     * The maximum number of files to be queued on disk in a given folders.
     * A batch size can sometimes be too big for some file systems to handle
     * efficiently.  Having this number lower than the batch size allows
     * to have large batches without having too many files in a single
     * directory.
     * @param maxPerFolder number of files queued per directory
     * @return maximum number of files queued per directory
     */
    private int maxPerFolder = DEFAULT_MAX_PER_FOLDER;

    /**
     * Whether to attempt committing any file leftovers in the committer
     * queue from a previous session when the committer is initialized.
     * Leftovers are typically associated with an abnormal termination.
     * @param commitLeftoversOnInit <code>true</code> to commit leftovers
     * @return <code>true</code> if committing leftovers
     */
    private boolean commitLeftoversOnInit = false;

    /**
     * Configuration only applicable when a commit fails.
     * @param onCommitFailure commit failure configuration
     * @return commit failure configuration
     */
    private final OnCommitFailure onCommitFailure = new OnCommitFailure();

    @Data
    @Accessors(chain = true)
    public static class OnCommitFailure {
        /**
         * Whether and how to split batches when re-attempting them.
         * See class documentation for details.
         * @param splitBatch split batch strategy
         * @return split batch strategy
         */
        private SplitBatch splitBatch = SplitBatch.OFF;

        /**
         * Maximum retries upon commit failures. Default is 0 (does not retry).
         * @param maxRetries maximum number of retries
         * @return maximum number of retries
         */
        private int maxRetries;

        /**
         * Delay in milliseconds between retries. Default is 0 (does not wait).
         * @param retryDelay delay between retries
         * @return delay between retries
         */
        private long retryDelay;

        /**
         * Whether to ignore non-critical errors in an attempt to keep going.
         * See class documentation for more details.
         * @param ignoreErrors <code>true</code> to ignore errors
         * @return <code>true</code> if ignoring errors
         */
        private boolean ignoreErrors;
    }
}
