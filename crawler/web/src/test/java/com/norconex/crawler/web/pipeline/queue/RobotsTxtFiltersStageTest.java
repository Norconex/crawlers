/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.queue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.pipeline.DocRecordPipelineContext;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.MockWebCrawlSession;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;
import com.norconex.crawler.web.doc.WebDocRecord;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.robot.RobotsTxt;
import com.norconex.crawler.web.robot.impl.StandardRobotsTxtProvider;

class RobotsTxtFiltersStageTest {

    public static class Configurer implements Consumer<CrawlSessionConfig> {
        @Override
        public void accept(CrawlSessionConfig cfg) {
            StandardRobotsTxtProvider provider =
                    new StandardRobotsTxtProvider() {
                @Override
                public synchronized RobotsTxt getRobotsTxt(
                        HttpFetcher fetcher, String url) {
                    try {
                        return parseRobotsTxt(IOUtils.toInputStream("""
                                User-agent: *

                                Disallow: /rejectMost/*
                                Allow: /rejectMost/butNotThisOne/*
                                """,
                                StandardCharsets.UTF_8), url, "test-crawler");
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };
            ((WebCrawlerConfig) cfg.getCrawlerConfigs().get(0))
                .setRobotsTxtProvider(provider);
        }
    }

    @MockWebCrawlSession(configConsumer = Configurer.class)
    @Test
    void testAllow(Crawler crawler) {
        // An allow for a robot rule should now be rejecting all non-allowing.
        // It should allows sub directories that have their parent rejected
        Assertions.assertFalse(testAllow(crawler,
                "http://rejected.com/rejectMost/blah.html"),
                "Matches Disallow");
        Assertions.assertTrue(testAllow(crawler,
                "http://accepted.com/rejectMost/butNotThisOne/blah.html"),
                "Matches Disallow AND Allow");
        Assertions.assertTrue(testAllow(crawler,
                "http://accepted.com/notListed/blah.html"),
                "No match in robot.txt");

    }

    private boolean testAllow(Crawler crawler, final String url) {
        var ctx = new DocRecordPipelineContext(
                crawler, new WebDocRecord(url, 0));
        var filterStage = new RobotsTxtFiltersStage();
        return filterStage.test(ctx);
    }
}