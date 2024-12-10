/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.web.cases.recovery;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.stubs.CrawlerConfigStubs;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that the right amount of docs are crawled after stoping
 * and starting the collector.
 */
@MockServerSettings
@Slf4j
@Disabled("Need refactor to work with new grid.")
class StartCleanAfterStopTest {

    @Test
    void testStartCleanAfterStop(
            ClientAndServer client, @TempDir Path tempDir) {
        var path = "/startCleanAfterStop";

        WebsiteMock.whenInfiniteDepth(client);

        //        var stopper = new CrawlSessionStopper();

        var cfg = CrawlerConfigStubs.memoryCrawlerConfig(tempDir);
        cfg.setStartReferences(List.of(serverUrl(client, path + "/0000")));
        cfg.setWorkDir(tempDir);
        cfg.setNumThreads(1);
        cfg.setMaxDepth(-1);
        cfg.setMaxDocuments(10);
        cfg.setMetadataChecksummer(null);
        cfg.setDocumentChecksummer(null);

        // First run should stop with 7 commits only (0-6)
        //        cfg.addEventListener(stopper);
        var outcome = ExternalCrawlSessionLauncher.start(cfg);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(
                outcome.getCommitterAfterLaunch()
                        .getUpsertCount()).isEqualTo(7);
        assertThat(
                WebTestUtil.lastSortedRequestReference(
                        outcome.getCommitterAfterLaunch())).isEqualTo(
                                WebsiteMock.serverUrl(client, path + "/0006"));

        // Second run, we clean and we should get 10 documents, including
        // the same first 7.
        //        cfg.removeEventListener(stopper);
        outcome = ExternalCrawlSessionLauncher.startClean(cfg);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isZero();
        assertThat(
                outcome.getCommitterAfterLaunch()
                        .getUpsertCount()).isEqualTo(10);
        assertThat(
                outcome.getCommitterCombininedLaunches()
                        .getUpsertCount()).isEqualTo(10);
        assertThat(
                WebTestUtil.lastSortedRequestReference(
                        outcome.getCommitterAfterLaunch())).isEqualTo(
                                WebsiteMock.serverUrl(client, path + "/0009"));
    }
}
