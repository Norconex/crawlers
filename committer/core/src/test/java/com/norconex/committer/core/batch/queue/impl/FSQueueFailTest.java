/* Copyright 2020-2023 Norconex Inc.
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

import java.util.Arrays;
import java.util.function.Consumer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.TestUtil;
import com.norconex.committer.core.batch.FailingBatchCommitter;
import com.norconex.committer.core.batch.queue.impl.FSQueue.SplitBatch;

/**
 */
class FSQueueFailTest {

    // write 8 upserts and 3 deletes.
    private final int[] commitRequests =
            { 1, -2, 3, 4, 5, -6, 7, 8, 9, -10, 11 };

    // Test that commit of a single batch fails twice but recovers OK
    // on third attempt.
    // No splitting attempt.
    @Test
    void testOnFailRetriable() throws CommitterException {
        var c = commitFailure(
                /* fail at doc: */ 6,
                /* recover at attempt: */ 3, q -> {
                    q.getConfiguration()
                            .setBatchSize(20)
                            .getOnCommitFailure()
                            .setMaxRetries(3);
                }
        );

        Assertions.assertEquals(3, c.getAttemptCount());
        Assertions.assertEquals(2, c.getExceptionCount());
        Assertions.assertEquals(commitRequests.length, c.getTotalSuccessDocs());
    }

    // Test that commit of a single batch fails until split to batches of 2.
    // No retrying.
    @Test
    void testOnFailSplitHalfDownTo2() throws CommitterException {
        var c = commitFailure(
                /* fail at doc: */ 3,
                /* recover at attempt: */ Integer.MAX_VALUE, q -> {
                    q.getConfiguration()
                            .setBatchSize(20)
                            .getOnCommitFailure()
                            .setMaxRetries(0)
                            .setSplitBatch(SplitBatch.HALF);
                }
        );

        // attempted batch sizes: 20, 10, 5, 3, 2(x6)
        Assertions.assertEquals(10, c.getAttemptCount());
        Assertions.assertEquals(4, c.getExceptionCount());
        Assertions.assertEquals(commitRequests.length, c.getTotalSuccessDocs());
    }

    // Test that commit of a single batch fails until split to batches of 1.
    // No retrying.
    @Test
    void testOnFailSplitHalfDownTo1() throws CommitterException {
        var c = commitFailure(
                /* fail at doc: */ 2,
                /* recover at attempt: */ Integer.MAX_VALUE, q -> {
                    q.getConfiguration()
                            .setBatchSize(20)
                            .getOnCommitFailure()
                            .setMaxRetries(0)
                            .setSplitBatch(SplitBatch.HALF);
                }
        );

        // attempted batch sizes: 20, 10, 5, 3, 2, 1(x11)
        Assertions.assertEquals(16, c.getAttemptCount());
        Assertions.assertEquals(5, c.getExceptionCount());
        Assertions.assertEquals(commitRequests.length, c.getTotalSuccessDocs());
    }

    // Test that commit of a single batch fails and is processed one by one.
    // No retrying.
    @Test
    void testOnFailSplitOneByOne() throws CommitterException {
        var c = commitFailure(
                /* fail at doc: */ 7,
                /* recover at attempt: */ Integer.MAX_VALUE, q -> {
                    q.getConfiguration()
                            .setBatchSize(20)
                            .getOnCommitFailure()
                            .setMaxRetries(0)
                            .setSplitBatch(SplitBatch.ONE);
                }
        );

        // attempted batch sizes: 20, 1(x11)
        Assertions.assertEquals(12, c.getAttemptCount());
        Assertions.assertEquals(1, c.getExceptionCount());
        Assertions.assertEquals(commitRequests.length, c.getTotalSuccessDocs());
    }

    // Test that all retries fails before processing one by one.
    @Test
    void testOnFailSplitOneByOneRetry() throws CommitterException {
        var c = commitFailure(
                /* fail at doc: */ 7,
                /* recover at attempt: */ Integer.MAX_VALUE, q -> {
                    q.getConfiguration()
                            .setBatchSize(20)
                            .getOnCommitFailure()
                            .setMaxRetries(3)
                            .setSplitBatch(SplitBatch.ONE);
                }
        );

        // attempted batch sizes: 20, 1(x11)    + 3 retry
        Assertions.assertEquals(15, c.getAttemptCount());
        // exceptions 4 at size 20, 0 when split
        Assertions.assertEquals(4, c.getExceptionCount());
        Assertions.assertEquals(commitRequests.length, c.getTotalSuccessDocs());
    }

    @Test
    void testOnFailNoSplitNoRepeat() throws CommitterException {
        var c = buildCommitter(
                /* fail at doc: */ 2,
                /* recover at attempt: */ Integer.MAX_VALUE, q -> {
                    q.getConfiguration()
                            .setBatchSize(4)
                            .getOnCommitFailure()
                            .setMaxRetries(0)
                            .setSplitBatch(SplitBatch.OFF);
                }
        );
        try {
            runCommitter(c);
            Assertions.fail("A CommitterException should have been thrown.");
        } catch (CommitterException e) {
            //NOOP
        }
        Assertions.assertEquals(1, c.getAttemptCount());
        Assertions.assertEquals(1, c.getExceptionCount());
        Assertions.assertEquals(0, c.getTotalSuccessDocs());
    }

    @Test
    void testOnFailIgnoreErrorsNoSplitNoRepeat() throws CommitterException {
        var c = commitFailure(
                /* fail at doc: */ 2,
                /* recover at attempt: */ 2, q -> {
                    q.getConfiguration()
                            .setBatchSize(3)
                            .getOnCommitFailure()
                            .setMaxRetries(0)
                            .setSplitBatch(SplitBatch.OFF)
                            .setIgnoreErrors(true);
                }
        );

        // attempted batch sizes: 3(x4)
        Assertions.assertEquals(4, c.getAttemptCount());
        // exceptions first 1 fails, rest OK
        Assertions.assertEquals(1, c.getExceptionCount());
        Assertions.assertEquals(
                commitRequests.length - 3, c.getTotalSuccessDocs()
        );
    }

    // Build & run Committer all at once
    FailingBatchCommitter commitFailure(
            int failAtDoc, int recoverAtAttempt, Consumer<FSQueue> c
    )
            throws CommitterException {
        var committer =
                buildCommitter(failAtDoc, recoverAtAttempt, c);
        runCommitter(committer);
        return committer;
    }

    // Build Committer
    FailingBatchCommitter buildCommitter(
            int failAtDoc, int recoverAtAttempt, Consumer<FSQueue> c
    )
            throws CommitterException {
        var committer = new FailingBatchCommitter(
                failAtDoc, recoverAtAttempt
        );
        var fsqueue = (FSQueue) committer.getConfiguration().getQueue();
        c.accept(fsqueue);
        return committer;
    }

    // Run Committer (attempt commit)
    void runCommitter(FailingBatchCommitter committer)
            throws CommitterException {

        Arrays.stream(Thread.currentThread().getStackTrace())
                .filter(s -> s.getMethodName().startsWith("test"))
                .findFirst()
                .map(StackTraceElement::getMethodName)
                .ifPresent(m -> Thread.currentThread().setName(m));

        var ctx = TestUtil.committerContext(null);
        ctx.getEventManager().setStacktraceLoggingDisabled(true);
        committer.init(ctx);
        TestUtil.commitRequests(
                committer, TestUtil.mixedRequests(commitRequests)
        );
        committer.close();
    }
}
