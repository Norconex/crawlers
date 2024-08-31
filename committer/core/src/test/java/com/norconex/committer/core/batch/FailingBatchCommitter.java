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

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;

import lombok.Getter;

// For simulating various batch-related conditions (e.g. failures)
public class FailingBatchCommitter
        extends AbstractBatchCommitter<BaseBatchCommitterConfig> {

    @Getter
    private final BaseBatchCommitterConfig configuration =
            new BaseBatchCommitterConfig();

    int attempts = 0;
    int exceptionCount = 0;
    int totalSuccessDocs = 0;

    private final int failAtDocIndex;
    private final int recoverAtAttemptIndex;

    public FailingBatchCommitter(
            int failAtDocIndex, int recoverAtAttemptIndex) {
        this.failAtDocIndex = failAtDocIndex;
        this.recoverAtAttemptIndex = recoverAtAttemptIndex;
    }

    public int getAttemptCount() {
        return attempts;
    }

    public int getExceptionCount() {
        return exceptionCount;
    }

    public int getTotalSuccessDocs() {
        return totalSuccessDocs;
    }

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {
        attempts++;
        var cnt = 0;
        while (it.hasNext()) {
            it.next();
            cnt++;
            if (attempts < recoverAtAttemptIndex && cnt >= failAtDocIndex) {
                exceptionCount++;
                throw new CommitterException(
                        "Mock committer failure. Attempt #"
                                + attempts + ". Doc #" + cnt);
            }
        }
        totalSuccessDocs += cnt;
    }
}
