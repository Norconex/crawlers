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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.committer.core.CommitterEvent;
import com.norconex.committer.core.service.CommitterServiceEvent;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.todo.usethis.annotations.SlowTest;

/**
 * CLI commands that execute full lifecycle except for actual crawling.
 * These verify that commands properly initialize, execute, and clean up
 * the crawler infrastructure.
 */
@SlowTest
class CliLifecycleCommandsTest {

    @TempDir
    private Path tempDir;

    @Test
    void testStartCommandLifecycleEvents() {
        var exit = TestCliCrawlerLauncher
                .builder()
                .args(List.of("start"))
                .workDir(tempDir)
                .build()
                .launch(oneDocConfig());

        assertThat(exit.getCode())
                .as("Crawler should start successfully")
                .isZero();

        // Verify lifecycle events occur in correct order
        String[] expectedEvents = {
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_BEGIN,
                CommitterEvent.COMMITTER_INIT_END,
                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
                CrawlerEvent.CRAWLER_CRAWL_BEGIN,
                CrawlerEvent.CRAWLER_CRAWL_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_BEGIN,
                CommitterEvent.COMMITTER_CLOSE_END,
                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
        };

        //TODO modify config so we do crawl a document (fake) so we get
        // more lifecycle events.

        //TODO mock the cluster or use faster infinispan config when we
        // know we are single node?

        assertThat(exit.getEventNames()).containsExactly(expectedEvents);
    }

    //
    //    @Test
    //    void testCleanCommand_ProducesCorrectEventSequence() throws IOException {
    //        var config = createMinimalConfig();
    //        var configFile = tempDir.resolve("config.yaml");
    //        BeanMapper.DEFAULT.write(config, configFile);
    //
    //        var exit = MockCliLauncher_DELETE
    //                .builder()
    //                .args("clean", "-config=" + configFile)
    //                .workDir(tempDir)
    //                .configModifier(cfg -> cfg.setClusterConnector(
    //                        new MockSingleNodeConnector()))
    //                .build()
    //                .launch();
    //
    //        assertThat(exit.getCode()).isZero();
    //
    //        String[] expectedEvents = {
    //                CommitterServiceEvent.COMMITTER_SERVICE_INIT_BEGIN,
    //                CommitterEvent.COMMITTER_INIT_BEGIN,
    //                CommitterEvent.COMMITTER_INIT_END,
    //                CommitterServiceEvent.COMMITTER_SERVICE_INIT_END,
    //                CrawlerEvent.CRAWLER_CLEAN_BEGIN,
    //                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_BEGIN,
    //                CommitterEvent.COMMITTER_CLEAN_BEGIN,
    //                CommitterEvent.COMMITTER_CLEAN_END,
    //                CommitterServiceEvent.COMMITTER_SERVICE_CLEAN_END,
    //                CrawlerEvent.CRAWLER_CLEAN_END,
    //                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_BEGIN,
    //                CommitterEvent.COMMITTER_CLOSE_BEGIN,
    //                CommitterEvent.COMMITTER_CLOSE_END,
    //                CommitterServiceEvent.COMMITTER_SERVICE_CLOSE_END
    //        };
    //
    //        assertThat(exit.getEvents())
    //                .extracting("name")
    //                .containsExactly(expectedEvents);
    //    }
    //
    private CrawlConfig noDocConfig() {
        var config = new CrawlConfig();
        config.setStartReferences(List.of("http://example.com/test"));
        config.setMaxDocuments(0); // Don't crawl anything
        return config;
    }

    private CrawlConfig oneDocConfig() {
        var config = new CrawlConfig();
        config.setStartReferences(List.of("http://example.com/test"));
        config.setMaxDocuments(0); // Don't crawl anything
        return config;
    }
}
