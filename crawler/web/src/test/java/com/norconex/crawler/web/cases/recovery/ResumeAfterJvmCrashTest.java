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
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.test.CrawlTestHarness;
import com.norconex.crawler.core.test.CrawlTestInstrument;
import com.norconex.crawler.web.WebCrawlDriverFactory;
import com.norconex.crawler.web.WebCrawlConfig;
import com.norconex.crawler.web.mocks.MockWebsite;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that the right amount of docs are crawled after crashing and
 * restarting the crawler from a forked JVM.
 */
@MockServerSettings
@Slf4j
@Timeout(180)
@SlowTest
class ResumeAfterJvmCrashTest {

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

    private static final int MAX_DOCS = 6;
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
    void testResumeAfterJvmCrash(ClientAndServer client,
            @TempDir Path tempDir)
            throws Exception {
        var path = "/resumeAfterJvmCrash";

        MockWebsite.whenBoundedDepth(client, SITE_DEPTH);
        // Wait for MockServer to register expectations before crawling
        waitForMockServerReady(client);

        //--- Run 1: crash the forked JVM mid-crawl ---
        try (var harness = new CrawlTestHarness(
                instrument(client, tempDir, path))) {
            var future = harness.launchAsync("node1");
            // Wait for at least one document to be fully imported before crashing
            // to ensure there's recoverable state for the resume run
            harness.waitFor(Duration.ofSeconds(45))
                    .anyNodeToHaveFired(
                            CrawlerEvent.DOCUMENT_IMPORTED);
            harness.crashNode("node1");
            harness.waitFor(Duration.ofSeconds(15))
                    .nodeToHaveDied("node1");
            var run1Result = future.get(10, TimeUnit.SECONDS);
            var run1Count = run1Result.getAllNodesEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED);
            assertThat(run1Count).isGreaterThanOrEqualTo(1);
        }

        //--- Run 2: resume after crash ---
        try (var harness = new CrawlTestHarness(
                instrument(client, tempDir, path))) {
            var run2Result = harness.launchSync("node1");
            var run2Count = run2Result.getAllNodesEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED);
            assertThat(run2Count).isGreaterThan(0);
        }

        //--- Run 3: next normal crawl discovers next batch of docs ---
        try (var harness = new CrawlTestHarness(
                instrument(client, tempDir, path))) {
            var run3Result = harness.launchSync("node1");
            var run3Count = run3Result.getAllNodesEventNameBag()
                    .getCount(CrawlerEvent.DOCUMENT_IMPORTED);
            assertThat(run3Count).isGreaterThan(0);
        }
    }

    private CrawlTestInstrument instrument(
            ClientAndServer client,
            Path tempDir,
            String path) {
        return new CrawlTestInstrument()
                .setDriverSupplierClass(
                        WebCrawlDriverFactory.class)
                .setRecordEvents(true)
                .setRecordInterval(Duration.ofSeconds(1))
                .setWorkDir(tempDir)
                .setNewJvm(true)
                .setClustered(false)
                .setConfigModifier(cfg -> {
                    var webCfg = (WebCrawlConfig) cfg;
                    webCfg.setId("test-resume-after-crash");
                    webCfg.setStartReferences(
                            List.of(serverUrl(
                                    client,
                                    path + "/0000")));
                    webCfg.setNumThreads(1);
                    webCfg.setMaxDepth(6);
                    webCfg.setMaxDocuments(MAX_DOCS);
                    webCfg.setMetadataChecksummer(null);
                    webCfg.setDocumentChecksummer(null);
                });
    }
}
