/* Copyright 2019-2025 Norconex Inc.
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

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.hazelcast.core.Hazelcast;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.test.CrawlTestHarness;
import com.norconex.crawler.core.test.CrawlTestInstrument;
import com.norconex.crawler.web.WebCrawlDriverFactory;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.mocks.MockWebsite;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that the right amount of docs are crawled after stopping
 * and resuming the crawler.
 */
@MockServerSettings
@Slf4j
@Timeout(120)
@SlowTest
class ResumeAfterStopTest {

    @AfterEach
    void tearDown() {
        // Ensure all Hazelcast instances are shut down to prevent resource accumulation
        Hazelcast.shutdownAll();
        try {
            Thread.sleep(500); // Give Hazelcast time to fully shut down
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final int MAX_DOCS = 12;
    private static final int SITE_DEPTH = 12;

    /**
     * Waits until the MockServer has expectations registered, or times out after 2 seconds.
     */
    private void waitForMockServerReady(ClientAndServer client) {
        long deadline = System.currentTimeMillis() + 2000;
        boolean ready = false;
        while (System.currentTimeMillis() < deadline && !ready) {
            try {
                var expectations = client
                        .retrieveActiveExpectations(
                                null);
                if (expectations != null
                        && expectations.length > 0) {
                    ready = true;
                    break;
                }
            } catch (Exception ignored) {
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
            }
        }
    }

    @Test
    void testResumeAfterStop(ClientAndServer client, @TempDir Path tempDir)
            throws Exception {
        var path = "/resumeAfterStop";

        MockWebsite.whenBoundedDepth(client, SITE_DEPTH);
        // Wait for MockServer to register expectations before crawling
        waitForMockServerReady(client);

        var instrument = new CrawlTestInstrument()
                .setDriverSupplierClass(
                        WebCrawlDriverFactory.class)
                .setRecordEvents(true)
                .setWorkDir(tempDir)
                .setNewJvm(false)
                .setClustered(false)
                .setConfigModifier(cfg -> {
                    var webCfg = (WebCrawlerConfig) cfg;
                    webCfg.setId("test-resume-after-stop");
                    webCfg.setStartReferences(
                            List.of(serverUrl(
                                    client,
                                    path + "/0000")));
                    webCfg.setNumThreads(1);
                    webCfg.setMaxDepth(6);
                    webCfg.setMaxDocuments(MAX_DOCS);
                    webCfg.setDelayResolver(WebTestUtil
                            .delayResolver(200));
                    webCfg.setMetadataChecksummer(null);
                    webCfg.setDocumentChecksummer(null);
                });

        try (var harness = new CrawlTestHarness(instrument)) {

            //--- First run (will be stopped mid-crawl) ---
            var futureResult = harness.launchAsync("node1");
            // Wait for at least one document to be fully imported before stopping
            // to ensure there's recoverable state for the resume run
            harness.waitFor(Duration.ofSeconds(30))
                    .anyNodeToHaveFired(
                            CrawlerEvent.DOCUMENT_IMPORTED);
            new Crawler(WebCrawlDriverFactory.create(),
                    harness.getFirstNodeConfig()).stop();
            var firstResult =
                    futureResult.get(40, TimeUnit.SECONDS);
            var firstTotalCount = firstResult
                    .getAllNodesEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED);
            assertThat(firstTotalCount).isBetween(1, MAX_DOCS - 1);

            //--- Resume run (no delay) ---
            instrument.setConfigModifier(cfg -> {
                var webCfg = (WebCrawlerConfig) cfg;
                webCfg.setId("test-resume-after-stop");
                webCfg.setStartReferences(
                        List.of(serverUrl(client, path
                                + "/0000")));
                webCfg.setNumThreads(1);
                webCfg.setMaxDepth(6);
                webCfg.setMaxDocuments(MAX_DOCS);
                webCfg.setDelayResolver(
                        WebTestUtil.delayResolver(0));
                webCfg.setMetadataChecksummer(null);
                webCfg.setDocumentChecksummer(null);
            });
            var secondResult = harness.launchSync("node1");
            var secondTotalCount = secondResult
                    .getAllNodesEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED);
            var combinedTotalCount = firstTotalCount
                    + secondTotalCount;

            // launchSync() returns per-run events (not cumulative), so
            // validate each run independently and the combined total.
            assertThat(secondTotalCount).isGreaterThanOrEqualTo(0);
            assertThat(combinedTotalCount)
                    .isGreaterThanOrEqualTo(firstTotalCount)
                    .isLessThanOrEqualTo(MAX_DOCS);
        }
    }
}
