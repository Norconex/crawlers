/* Copyright 2016-2024 Norconex Inc.
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
package com.norconex.crawler.web.doc.pipelines.queue.stages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core.tasks.CrawlerTaskContext;
import com.norconex.crawler.core.tasks.crawl.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.robot.RobotsTxt;
import com.norconex.crawler.web.robot.impl.StandardRobotsTxtProvider;
import com.norconex.crawler.web.stubs.CrawlerStubs;

class RobotsTxtFiltersStageTest {

    @TempDir
    private Path tempDir;

    @Test
    void testAllow() {
        var crawler = CrawlerStubs.memoryCrawlerTaskContext(tempDir, cfg -> cfg
                .setRobotsTxtProvider(new StandardRobotsTxtProvider() {
                    @Override
                    public synchronized RobotsTxt getRobotsTxt(
                            HttpFetcher fetcher, String url) {
                        try {
                            return parseRobotsTxt(
                                    IOUtils.toInputStream("""
                                            User-agent: *

                                            Disallow: /rejectMost/*
                                            Allow: /rejectMost/butNotThisOne/*
                                            """,
                                            StandardCharsets.UTF_8),
                                    url,
                                    "test-crawler");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }));

        // An allow for a robot rule should now be rejecting all non-allowing.
        // It should allows sub directories that have their parent rejected
        Assertions.assertFalse(
                testAllow(
                        crawler,
                        "http://rejected.com/rejectMost/blah.html"),
                "Matches Disallow");
        Assertions.assertTrue(
                testAllow(
                        crawler,
                        "http://accepted.com/rejectMost/butNotThisOne/blah.html"),
                "Matches Disallow AND Allow");
        Assertions.assertTrue(
                testAllow(
                        crawler,
                        "http://accepted.com/notListed/blah.html"),
                "No match in robot.txt");
    }

    private boolean testAllow(CrawlerTaskContext crawler, final String url) {
        var ctx = new QueuePipelineContext(
                crawler, new WebCrawlDocContext(url, 0));
        var filterStage = new RobotsTxtFiltersStage();
        return filterStage.test(ctx);
    }
}