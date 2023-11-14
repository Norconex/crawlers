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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.TestUtil;


class FSQueueTest {

    @TempDir
    static Path folder;

    private CommitterContext ctx;
    private FSQueue queue;

    @BeforeEach
    public void setup() {
        ctx = CommitterContext.builder().setWorkDir(folder).build();
        queue = new FSQueue();
    }

    @Test
    void testQueue() throws CommitterException, IOException {

        final var batchQty = new MutableInt();
        final Set<String> batchRefs = new TreeSet<>();

        queue.getConfiguration()
            .setCommitLeftoversOnInit(true)
            .setBatchSize(5);
        queue.init(ctx, it -> {
            batchQty.increment();
            while (it.hasNext()) {
                var req = it.next();
                batchRefs.add(req.getReference());
            }
        });

        // Add test data
        for (var i = 0; i < 13; i++) {
            queue.queue(TestUtil.upsertRequest(i + 1));
        }
        queue.close();

        // records should have been processed in 3 batches.
        Assertions.assertEquals(3, batchQty.getValue());

        // There should be 13 obtained from queue in total
        Assertions.assertEquals(13, batchRefs.size());

        // Queue directory should be empty.
        Assertions.assertEquals(0, Files.find(folder,  1,
                (f, a) -> f.toFile().getName().endsWith(
                        FSQueueUtil.EXT)).count());

        assertThat(queue.getBatchConsumer()).isNotNull();
    }

    @Test
    void testWriteRead() {
        var q = new FSQueue();
        q.getConfiguration()
            .setBatchSize(50)
            .setMaxPerFolder(100)
            .setCommitLeftoversOnInit(true)
            .getOnCommitFailure()
                .setIgnoreErrors(true)
                .setMaxRetries(6)
                .setRetryDelay(666);

        assertThatNoException().isThrownBy(() -> {
            TestUtil.beanMapper().assertWriteRead(q);
        });
        assertThat(q.getConfiguration().getOnCommitFailure().getMaxRetries())
                .isEqualTo(6);
        assertThat(q.getConfiguration().getOnCommitFailure().getRetryDelay())
                .isEqualTo(666);
    }
}
