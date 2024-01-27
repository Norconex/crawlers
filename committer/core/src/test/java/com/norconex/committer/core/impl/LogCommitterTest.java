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
package com.norconex.committer.core.impl;

import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.TestUtil;
import com.norconex.committer.core.impl.LogCommitterConfig.LogLevel;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.text.TextMatcher;

/**
 * <p>LogCommitter tests.</p>
 */
class LogCommitterTest  {

    @Test
    void testMemoryCommitter() throws CommitterException {
        assertThatNoException().isThrownBy(() -> {
            // write 5 upserts and 2 deletes.
            var c = new LogCommitter();
            c.init(TestUtil.committerContext(null));
            TestUtil.commitRequests(
                    c, TestUtil.mixedRequests(1, 0, 1, 1, 1, 0, 1));
            c.close();
        });
    }

    @Test
    void testWriteRead() {
        assertThatNoException().isThrownBy(() -> {
            var c = new LogCommitter();
            c.getConfiguration()
                .setFieldMatcher(TextMatcher.wildcard("pot?to"))
                .setIgnoreContent(true)
                .setLogLevel(LogLevel.ERROR);
            BeanMapper.DEFAULT.assertWriteRead(c);
        });
    }
}
