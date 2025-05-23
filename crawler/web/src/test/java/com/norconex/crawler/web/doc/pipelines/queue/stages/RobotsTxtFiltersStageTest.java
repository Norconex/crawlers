/* Copyright 2016-2025 Norconex Inc.
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

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;

import com.norconex.crawler.core.doc.pipelines.queue.QueuePipelineContext;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.web.doc.WebCrawlDocContext;
import com.norconex.crawler.web.doc.operations.robot.RobotsTxt;
import com.norconex.crawler.web.doc.operations.robot.impl.StandardRobotsTxtProvider;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.util.Web;

class RobotsTxtFiltersStageTest {

    @WebCrawlTest(focus = Focus.CONTEXT)
    void testAllow(CrawlContext ctx) {
        Web.config(ctx).setRobotsTxtProvider(new StandardRobotsTxtProvider() {
            @Override
            public synchronized RobotsTxt getRobotsTxt(
                    Fetcher fetcher, String url) {
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
        });

        // An allow for a robot rule should now be rejecting all non-allowing.
        // It should allows sub directories that have their parent rejected
        Assertions.assertFalse(testAllow(
                ctx, "http://rejected.com/rejectMost/blah.html"),
                "Matches Disallow");
        Assertions.assertTrue(testAllow(
                ctx,
                "http://accepted.com/rejectMost/butNotThisOne/blah.html"),
                "Matches Disallow AND Allow");
        Assertions.assertTrue(testAllow(
                ctx,
                "http://accepted.com/notListed/blah.html"),
                "No match in robot.txt");
    }

    private boolean testAllow(CrawlContext crawlerCtx, final String url) {
        var queueCtx = new QueuePipelineContext(
                crawlerCtx, new WebCrawlDocContext(url, 0));
        var filterStage = new RobotsTxtFiltersStage();
        return filterStage.test(queueCtx);
    }
}
