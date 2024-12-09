/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.commands.crawl.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.function.Consumer;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;

class CoreQueueInitializerTest {

    public static class FromFilesConfigModifier
            implements Consumer<CrawlerConfig> {
        @Override
        public void accept(CrawlerConfig cfg) {
            var file1 = cfg.getWorkDir().resolve("start-file1.txt");
            var file2 = cfg.getWorkDir().resolve("start-file2.txt");
            try {
                Files.writeString(file1, "ref1\nref2\nref3");
                Files.writeString(file2, "ref4\nref5\n");
                cfg.setStartReferencesFiles(List.of(file1, file2));
            } catch (IOException e) {
                fail(e);
            }
        }
    }

    public static class FromProvidersConfigModifier
            implements Consumer<CrawlerConfig> {
        @Override
        public void accept(CrawlerConfig cfg) {
            cfg.setStartReferencesProviders(List.of(
                    () -> List.of("ref1", "ref2").iterator(),
                    () -> List.of("ref3", "ref4", "ref5").iterator()));
        }
    }

    @CrawlTest(
        focus = Focus.CRAWL,
        config = """
                numThreads: 2
                """,
        configModifier = FromFilesConfigModifier.class
    )
    void testFromFiles(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(5);
    }

    @CrawlTest(
        focus = Focus.CRAWL,
        config = """
                numThreads: 2
                """,
        configModifier = FromProvidersConfigModifier.class
    )
    void testFromProviders(MemoryCommitter mem) {
        assertThat(mem.getUpsertCount()).isEqualTo(5);
    }

}
