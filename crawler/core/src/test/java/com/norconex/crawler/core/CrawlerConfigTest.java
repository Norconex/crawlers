/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.crawler.core;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.core.mocks.MockNoopDataStoreEngine;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.core.stubs.CrawlerConfigStubs;


class CrawlerConfigTest {

    @Test
    void testCrawlerConfig(@TempDir Path tempDir) {
        assertThatNoException().isThrownBy(() ->
            BeanMapper
            .builder()
            .polymorphicTypeImpl(
                    DataStoreEngine.class,
                    List.of(MockNoopDataStoreEngine.class))
            .build().assertWriteRead(
                       CrawlerConfigStubs.randomMemoryCrawlerConfig(tempDir)));
    }
}
