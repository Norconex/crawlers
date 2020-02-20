/* Copyright 2019-2020 Norconex Inc.
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
package com.norconex.collector.http.web.feature;

import java.io.PrintWriter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.crawler.URLCrawlScopeStrategy;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.IAddOperation;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * Test extraction of JavaScript URLs.
 * @author Pascal Essiembre
 */
// Test case for https://github.com/Norconex/collector-http/issues/540
public class JavaScriptURL extends AbstractTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        URLCrawlScopeStrategy scope = new URLCrawlScopeStrategy();
        scope.setStayOnPort(true);
        cfg.setUrlCrawlScopeStrategy(scope);
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {
        // 1 links must be extracted, and two pages crawled/committed in total
        boolean isGoodUrl = Boolean.valueOf(req.getParameter("goodurl"));
        if (!isGoodUrl) {
            out.println("<h1>Page with a Javascript URL</h1>");
            out.println("<a href=\"javascript:some_function("
                    + "'some_arg', 'another_arg');\">Must be skipped</a>");
            out.println("<a href=\"javascript&#x3a;abcd_Comments&#x28;true"
                    + "&#x29;&#x3b;\">Must also be skipped</a>");
            out.println("<a href=\"?goodurl=true\">Must be followed</a>");
            out.println("Must be crawled (1 of 2)");
        } else {
            out.println("<h1>Page with a Javascript URL</h1>");
            out.println("Must be crawled (2 of 2)");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        List<IAddOperation> docs = committer.getAddOperations();

        assertListSize("document", docs, 2);

        for (IAddOperation doc : docs) {
            String content = content(doc);
            if (!doc.getReference().contains("goodurl=true")) {
                // first page
                Assertions.assertTrue(
                        content.contains("Must be crawled (1 of 2)"),
                        "First page not crawled properly.");
                Assertions.assertEquals(1, doc.getMetadata().get(
                        HttpDocMetadata.REFERENCED_URLS).size(),
                        "Only 1 URL should have been extracted.");
            } else {
                // second page
                Assertions.assertTrue(
                        content.contains("Must be crawled (2 of 2)"),
                        "Script page not crawled properly.");
            }
        }
    }
}
