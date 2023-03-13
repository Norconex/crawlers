/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.web.session.feature;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerEvent;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebStubber;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.WebsiteMock;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that max concurrent crawler setting is respected.
 */
@MockServerSettings
@Slf4j
class MaxConcurrentCrawlersTest {

    @ParameterizedTest
    // max concurrent crawlers, total crawlers
    @CsvSource(textBlock =
        """
        -1, 1
        -1, 3
         1, 3
         2, 3
         3, 3
         1, 1
         3, 1
        """)
    void testMaxConcurrentCrawlers(
            int maxConcurrentCrawlers, int totalCrawlers,
            ClientAndServer client) throws IOException {

        var basePath = "/maxConcurrent/";

        client.reset();
        WebsiteMock.whenInfinitDepth(client);

        var startPath = basePath + "0";
        var totalRunning = new AtomicInteger();
        var maxDetectedAtOnce = new AtomicInteger();
        var maxDocs = 5;

        var crawlSession = TestWebCrawlSession
            .forStartUrls()
            .crawlSessionSetup(sessCfg -> {
                // add requested num of crawler and max concurrent
                sessCfg.setMaxConcurrentCrawlers(maxConcurrentCrawlers);

                // register number of crawl begin vs close and at any
                // given time there should not be more than max concurrent.
                sessCfg.addEventListener(event -> {
                    if (event.is(CrawlerEvent.CRAWLER_INIT_BEGIN)) {
                        var total = totalRunning.incrementAndGet();
                        var max = maxDetectedAtOnce.updateAndGet(
                                cur -> Math.max(cur, total));
                        LOG.debug("A crawler started. Total running: {} "
                                + "(max detected: {}).", total, max);
                    } else if (event.is(CrawlerEvent.CRAWLER_STOP_END)) {
                        // we rely on stop event because that's what is
                        // triggered when max document is reached.
                        var total = totalRunning.decrementAndGet();
                        LOG.debug("A crawler ended. Total running: {} "
                                + "(max detected: {}).",
                                total, maxDetectedAtOnce.get());
                    }
                });

                var crawlerConfigs = new ArrayList<CrawlerConfig>();
                for (var i = 0; i < totalCrawlers; i++) {
                    var cfg = WebStubber.crawlerConfig();
                    cfg.setId("Crawler %s of %s, max concurr: %s"
                            .formatted(
                                    i+1,
                                    totalCrawlers,
                                    maxConcurrentCrawlers));
                    cfg.setStartURLs(serverUrl(client, startPath));
                    cfg.setMaxDepth(-1);
                    cfg.setNumThreads(1);
                    cfg.setMaxDocuments(maxDocs);
                    // Delay a bit to give time to for threads to exist
                    // concurrently.
                    cfg.setDelayResolver((robot, url) -> {
                        Sleeper.sleepMillis(100);
                    });
                    crawlerConfigs.add(cfg);
                }
                sessCfg.setCrawlerConfigs(crawlerConfigs);
            })
            .crawlSession();
        WebTestUtil.ignoreAllIgnorables(crawlSession);
        crawlSession.start();

        if (maxConcurrentCrawlers <= 0) {
            // if not limiting concurrent crawler, it should match total
            assertThat(maxDetectedAtOnce.get()).isEqualTo(totalCrawlers);
        } else {
            // if limiting concurrent crawler, it should match max concurr.
            assertThat(maxDetectedAtOnce.get())
                    .isLessThanOrEqualTo(maxConcurrentCrawlers);
        }
        for (var crawler : crawlSession.getCrawlers()) {
            var mem = WebTestUtil.getFirstMemoryCommitter(crawler);
            assertThat(mem.getRequestCount()).isEqualTo(maxDocs);
        }

        crawlSession.clean();
    }
}
