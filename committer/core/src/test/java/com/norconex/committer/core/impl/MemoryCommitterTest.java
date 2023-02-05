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
package com.norconex.committer.core.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.TestUtil;
import com.norconex.commons.lang.text.TextMatcher;

/**
 * <p>MemoryCommitter tests.</p>
 */
class MemoryCommitterTest  {

    @Test
    void testMemoryCommitter() throws CommitterException {
        // write 5 upserts and 2 deletes.
        var c = new MemoryCommitter();

        c.init(TestUtil.committerContext(null));
        TestUtil.commitRequests(c, TestUtil.mixedRequests(1, 0, 1, 1, 1, 0, 1));
        c.close();

        assertThat(c.getRequestCount()).isEqualTo(7);
        assertThat(c.getUpsertCount()).isEqualTo(5);
        assertThat(c.getDeleteCount()).isEqualTo(2);


        c.setIgnoreContent(true);
        c.setFieldMatcher(TextMatcher.basic("blah"));
        c.clean();
        TestUtil.commitRequests(c, TestUtil.mixedRequests(1, 0, 1, 1, 1, 0, 1));
        assertThat(c.isIgnoreContent()).isTrue();
        assertThat(c.getFieldMatcher()).isEqualTo(TextMatcher.basic("blah"));

        assertThat(c.getRequestCount()).isEqualTo(7);
        assertThat(c.getUpsertCount()).isEqualTo(5);
        assertThat(c.getDeleteCount()).isEqualTo(2);

        assertThat(c.getAllRequests()).hasSize(7);
        assertThat(c.getUpsertRequests()).hasSize(5);
        assertThat(c.getDeleteRequests()).hasSize(2);
    }
}
