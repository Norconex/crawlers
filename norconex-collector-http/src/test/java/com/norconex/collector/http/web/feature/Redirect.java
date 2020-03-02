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
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.IAddOperation;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.importer.doc.DocMetadata;

/**
 * The final URL of a redirect should be stored so relative links in it
 * are relative to final URL, not the first.  Github issue #17.
 * @author Pascal Essiembre
 */
public class Redirect extends AbstractTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        crawlerConfig.setMaxDepth(0);
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {
        if (req.getPathInfo() == null
                || !req.getPathInfo().contains("redirected")) {
            resp.sendRedirect(req.getRequestURL() + "/redirected/page.html");
            return;
        }
        out.println("<h1>Redirected test page</h1>");
        out.println("The URL was redirected."
                + "The URLs on this page should be relative to /"
                + getPath() + "/redirected/ and not /.  The crawler should "
                + "redirect and figure that out.<br><br>");
        out.println("<a href=\"page1.html\">Page 1 (broken)</a>");
        out.println("<a href=\"page2.html\">Page 2 (broken)</a>");
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        assertListSize("document", committer.getAddOperations(), 1);

        IAddOperation doc = committer.getAddOperations().get(0);
        String ref = doc.getReference();

        List<String> urls = doc.getMetadata().getStrings(DocMetadata.REFERENCE);
        assertListSize("URL", urls, 1);

        Assertions.assertTrue(
                ref.contains("/" + getPath() + "/redirected"),
                "Invalid redirection URL: " + ref);

        List<String> inPageUrls = doc.getMetadata().getStrings(
                HttpDocMetadata.REFERENCED_URLS);

        assertListSize("referenced URLs", inPageUrls, 2);

        Assertions.assertTrue(inPageUrls.get(0).matches(
                ".*/" + getPath() + "/redirected/page[12].html"),
                "Invalid relative URL: " + inPageUrls.get(0));
        Assertions.assertTrue(inPageUrls.get(1).matches(
                ".*/" + getPath() + "/redirected/page[12].html"),
                "Invalid relative URL: " + inPageUrls.get(1));
    }
}
