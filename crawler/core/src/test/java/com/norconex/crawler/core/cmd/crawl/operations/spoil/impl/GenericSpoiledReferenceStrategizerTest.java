/* Copyright 2017-2024 Norconex Inc.
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
package com.norconex.crawler.core.cmd.crawl.operations.spoil.impl;

import static com.norconex.crawler.core.cmd.crawl.operations.spoil.impl.GenericSpoiledReferenceStrategizerConfig.DEFAULT_FALLBACK_STRATEGY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.core.cmd.crawl.operations.spoil.SpoiledReferenceStrategy;
import com.norconex.crawler.core.doc.DocResolutionStatus;

class GenericSpoiledReferenceStrategizerTest {

    @Test
    void testWriteRead() {
        var s = new GenericSpoiledReferenceStrategizer();
        s.getConfiguration()
                .setFallbackStrategy(SpoiledReferenceStrategy.GRACE_ONCE)
                .setMapping(
                        DocResolutionStatus.MODIFIED,
                        SpoiledReferenceStrategy.IGNORE)
                .setMapping(
                        DocResolutionStatus.BAD_STATUS,
                        SpoiledReferenceStrategy.DELETE);
        assertThatNoException().isThrownBy(() -> {
            BeanMapper.DEFAULT.assertWriteRead(s);
        });
    }

    @Test
    void testNoStrategy() {
        var s = new GenericSpoiledReferenceStrategizer();
        s.getConfiguration().setFallbackStrategy(null);
        assertThat(s.resolveSpoiledReferenceStrategy(
                "ref", DocResolutionStatus.MODIFIED)).isSameAs(
                        DEFAULT_FALLBACK_STRATEGY);
    }
}
