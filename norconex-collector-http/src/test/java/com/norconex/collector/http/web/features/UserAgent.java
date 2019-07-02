/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.features;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.IAddOperation;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * Test that the user agent is sent properly to web servers with the
 * default HTTP Fetcher.
 * @author Pascal Essiembre
 */
public class UserAgent extends AbstractTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        crawlerConfig.setMaxDepth(0);
        ((GenericHttpFetcher) crawlerConfig.getHttpFetchers()
                .get(0)).getConfig().setUserAgent("Super Secret Agent");
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, PrintWriter out) throws Exception {
        String userAgent = req.getHeader("User-Agent");
        out.println("<h1>User Agent test page</h1>");
        out.println("The user agent is: " + userAgent);
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        assertListSize("document", committer.getAddOperations(), 1);
        IAddOperation doc = committer.getAddOperations().get(0);
        Assertions.assertTrue(content(doc).contains("Super Secret Agent"),
                "Wrong or undetected User-Agent.");
    }
}
