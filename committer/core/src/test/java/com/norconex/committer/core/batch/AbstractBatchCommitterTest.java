/* Copyright 2022 Norconex Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.TestBatchCommitter;
import com.norconex.committer.core.TestMemoryQueue;
import com.norconex.committer.core.TestUtil;
import com.norconex.commons.lang.xml.XML;

class AbstractBatchCommitterTest {

    @Test
    void testAbstractBatchCommitter() throws CommitterException {
        try (var c = new TestBatchCommitter()) {
            var ctx = CommitterContext.builder().build();

            // no committer queue does not throw
            c.setCommitterQueue(null);
            assertThatNoException().isThrownBy(() -> {
                c.init(ctx);
            });

            var queue = new TestMemoryQueue();
            c.setCommitterQueue(queue);

            c.init(ctx);

            c.upsert(TestUtil.upsertRequest(1));
            c.delete(TestUtil.deleteRequest(1));
            c.upsert(TestUtil.upsertRequest(2));
            c.delete(TestUtil.deleteRequest(2));
            c.upsert(TestUtil.upsertRequest(3));
            c.upsert(TestUtil.upsertRequest(4));

            assertThat(queue.getAllRequests()).hasSize(6);
            assertThat(queue.getUpsertRequests()).hasSize(4);
            assertThat(queue.getDeleteRequests()).hasSize(2);

            c.clean();
            assertThat(queue.getAllRequests()).isEmpty();
            assertThat(queue.getUpsertRequests()).isEmpty();
            assertThat(queue.getDeleteRequests()).isEmpty();

            assertThat(c.getCommitterQueue()).isSameAs(queue);


            assertThatNoException().isThrownBy(() -> {
                c.saveToXML(new XML("committer"));
                c.loadFromXML(new XML("committer"));
            });

        }
    }
}
