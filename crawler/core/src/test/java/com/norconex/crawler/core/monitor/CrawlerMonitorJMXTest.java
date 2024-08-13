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
package com.norconex.crawler.core.monitor;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.CoreStubber;

class CrawlerMonitorJMXTest {

    @TempDir
    private Path tempDir;

    @Test
    void testCrawlerMonitorJMX() {
        assertThatNoException().isThrownBy(() -> {
            var crawler = CoreStubber.crawler(tempDir);
            crawler.getService().start();
            CrawlerMonitorJMX.register(crawler);
            CrawlerMonitorJMX.unregister(crawler);
        });
    }
}
