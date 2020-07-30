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

import javax.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.link.impl.HtmlLinkExtractor;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Content of &lt;script&gt; tags must be stripped by GenericLinkExtractor
 * but src must be followed.
 * @author Pascal Essiembre
 */
// Test case for https://github.com/Norconex/collector-http/issues/232
public class ScriptTags extends AbstractTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        HtmlLinkExtractor le = new HtmlLinkExtractor();
        le.addLinkTag("script", "src");
        crawlerConfig.setLinkExtractors(le);
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, PrintWriter out) throws Exception {
        boolean isScript = Boolean.valueOf(req.getParameter("script"));
        if (!isScript) {
            out.println("<h1>Page with a script tag</h1>");
            out.println("<script src=\"" + req.getRequestURL()
                + "?script=true\">"
                + "THIS_MUST_BE_STRIPPED, but src URL must be crawled"
                + "</script>");
            out.println("<script>THIS_MUST_BE_STRIPPED</script>");
            out.println("View the source to see &lt;script&gt; tags");
        } else {
            out.println("<h1>The Script page</h1>");
            out.println("This must be crawled.");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        assertListSize("document", committer.getUpsertRequests(), 2);
        for (UpsertRequest doc : committer.getUpsertRequests()) {
            String content = content(doc);
            if (!doc.getReference().contains("script=true")) {
                // first page
                Assertions.assertTrue(
                        content.contains("View the source"),
                        "First page not crawled properly");
                Assertions.assertTrue(
                        !content.contains("THIS_MUST_BE_STRIPPED"),
                        "Did not strip inside of <script>");
            } else {
                // second page
                Assertions.assertTrue(
                        content.contains("This must be crawled"),
                        "Script page not crawled properly");
            }
        }
    }
}
