/* Copyright 2022-2025 Norconex Inc.
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
package com.norconex.crawler.core.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.importer.ImporterEvent;

/**
 * CLI commands that execute full lifecycle except for actual crawling.
 * These verify that commands properly initialize, execute, and clean up
 * the crawler infrastructure.
 */
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class CliLifecycleCommandsTest {

    @TempDir
    private Path tempDir;

    @Test
    void testStartCommandEventSequence() {
        var exit = TestCliCrawlerLauncher
                .builder()
                .args(List.of("start"))
                .workDir(tempDir)
                .build()
                .launch(twoDocsConfig());

        assertThat(exit.getCode())
                .as("Crawler should start successfully")
                .isZero();

        // Verify lifecycle events occur in correct order
        // Since we're crawling one document, we expect document processing
        // events
        String[] expectedEvents = {
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.DOCUMENT_QUEUED,
                CrawlerEvent.DOCUMENT_QUEUED,
                ImporterEvent.IMPORTER_HANDLER_BEGIN,
                ImporterEvent.IMPORTER_HANDLER_END,
                CrawlerEvent.DOCUMENT_IMPORTED,
                CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_BEGIN,
                CommitterEvent.COMMITTER_ACCEPT_YES,
                CommitterEvent.COMMITTER_UPSERT_BEGIN,
                CommitterEvent.COMMITTER_UPSERT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END,
                ImporterEvent.IMPORTER_HANDLER_BEGIN,
                ImporterEvent.IMPORTER_HANDLER_END,
                CrawlerEvent.DOCUMENT_IMPORTED,
                CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_BEGIN,
                CommitterEvent.COMMITTER_ACCEPT_YES,
                CommitterEvent.COMMITTER_UPSERT_BEGIN,
                CommitterEvent.COMMITTER_UPSERT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
        };

        assertThat(exit.getEventNames()).containsExactly(expectedEvents);

    }

    @Test
    void testCleanCommandEventSequence() {
        var exit = TestCliCrawlerLauncher
                .builder()
                .args(List.of("clean"))
                .workDir(tempDir)
                .build()
                .launch(twoDocsConfig());

        assertThat(exit.getCode()).isZero();

        String[] expectedEvents = {
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_END,
                CrawlerEvent.CRAWLER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
        };

        assertThat(exit.getEventNames()).containsExactly(expectedEvents);
    }

    @Test
    void testStartCleanCommandEventSequence() {
        var exit = TestCliCrawlerLauncher
                .builder()
                .args(List.of("start", "-clean"))
                .workDir(tempDir)
                .build()
                .launch(oneDocConfig());

        assertThat(exit.getCode()).isZero();

        String[] expectedEvents = {
                // Init
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,

                // Perform cleaning
                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_BEGIN,
                CommitterEvent.COMMITTER_CLEAN_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_END,
                CrawlerEvent.CRAWLER_CLEAN_END,

                // Regular crawl flow
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.DOCUMENT_QUEUED,
                ImporterEvent.IMPORTER_HANDLER_BEGIN,
                ImporterEvent.IMPORTER_HANDLER_END,
                CrawlerEvent.DOCUMENT_IMPORTED,
                CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_BEGIN,
                CommitterEvent.COMMITTER_ACCEPT_YES,
                CommitterEvent.COMMITTER_UPSERT_BEGIN,
                CommitterEvent.COMMITTER_UPSERT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_UPSERT_END,
                CrawlerEvent.CRAWLER_CRAWL_END,

                // Close
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
        };

        assertThat(exit.getEventNames()).containsExactly(expectedEvents);
    }

    private CrawlConfig twoDocsConfig() {
        var config = new CrawlConfig();
        config.setStartReferences(List.of(
                "http://example.com/test1",
                "http://example.com/test2"));
        config.setMaxDocuments(2);
        return config;
    }

    private CrawlConfig oneDocConfig() {
        var config = new CrawlConfig();
        config.setNumThreads(1);
        config.setStartReferences(List.of(
                "http://example.com/test1"));
        config.setMaxDocuments(1);
        return config;
    }
}
