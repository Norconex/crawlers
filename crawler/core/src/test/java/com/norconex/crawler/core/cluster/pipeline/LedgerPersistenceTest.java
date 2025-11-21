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
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

/**
 * Verifies that ledger-backed persistence does not prevent multiple runs
 * against the same work directory when RocksDB persistence is enabled.
 *
 * Rather than introspecting internal ledger state, this test focuses on the
 * observable contract: running the same crawler twice with the same work
 * directory must complete without any RocksDB lock errors.
 */
class LedgerPersistenceTest {

    @TempDir
    private Path tempDir;

    @Test
    @Timeout(60)
    void testMultipleRunsReusePersistentStateWithoutLocking() {
        var workDir = tempDir.resolve("work");
        var driver = TestCrawlDriverFactory.create();

        var config = baseConfig(workDir);

        // First run: processes the references and initializes RocksDB
        assertThatNoException()
                .as("first run with persistence should succeed")
                .isThrownBy(() -> new Crawler(driver, config).crawl());

        // Second run: same workDir & crawler id should be able to
        // open and reuse RocksDB-backed caches without lock failures.
        var secondConfig = baseConfig(workDir);

        assertThatNoException()
                .as("second run with same workDir should also succeed")
                .isThrownBy(() -> new Crawler(driver, secondConfig).crawl());
    }

    private CrawlConfig baseConfig(Path workDir) {
        return new CrawlConfig()
                .setId("ledger-persistence-test")
                .setWorkDir(workDir)
                .setStartReferences(List.of("ref-1", "ref-2", "ref-3"))
                .setFetchers(List.of(Configurable.configure(
                        new MockFetcher(), cfg -> cfg.setDelay(
                                Duration.ofMillis(10)))))
                .setNumThreads(1);
    }
}
