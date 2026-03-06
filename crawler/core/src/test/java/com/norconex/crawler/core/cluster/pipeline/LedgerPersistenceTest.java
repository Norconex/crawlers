/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.cluster.pipeline;

import static org.assertj.core.api.Assertions.assertThatNoException;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;
import com.norconex.crawler.core.test.CrawlTestDriver;

/**
 * Verifies that ledger-backed persistence does not prevent multiple runs
 * against the same work directory.
 *
 * Running the same crawler twice with the same work directory must complete
 * without any lock errors.
 */
@Timeout(60)
class LedgerPersistenceTest {

    @TempDir
    private Path tempDir;

    @Test
    void testMultipleRunsReusePersistentStateWithoutLocking() {
        var workDir = tempDir.resolve("work");
        var driver = CrawlTestDriver.create();

        var config = baseConfig(workDir);

        // First run: processes the references and initializes the store
        assertThatNoException()
                .as("first run with persistence should succeed")
                .isThrownBy(() -> new Crawler(driver, config)
                        .crawl());

        // Second run: same workDir & crawler id should be able to
        // reuse stores without lock failures.
        var secondConfig = baseConfig(workDir);

        assertThatNoException()
                .as("second run with same workDir should also succeed")
                .isThrownBy(() -> new Crawler(driver,
                        secondConfig).crawl());
    }

    private CrawlConfig baseConfig(Path workDir) {
        return new CrawlConfig()
                .setId("ledger-persistence-test")
                .setWorkDir(workDir)
                .setStartReferences(List.of("ref-1", "ref-2",
                        "ref-3"))
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(),
                        cfg -> cfg.setDelay(
                                Duration.ofMillis(
                                        10)))))
                .setNumThreads(1)
                .setIdleTimeout(Duration.ofMillis(500));
    }
}
