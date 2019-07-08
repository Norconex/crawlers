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
package com.norconex.collector.http.web.feature;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * Test (non)canonical link detection.
 * @author Pascal Essiembre
 */
public class CanonicalLink extends AbstractTestFeature {

    private final MutableInt canCount = new MutableInt();

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        canCount.setValue(0);
        crawlerConfig.setIgnoreCanonicalLinks(false);
        crawlerConfig.addEventListeners(e -> {
            if (e.is(HttpCrawlerEvent.REJECTED_NONCANONICAL)) {
                canCount.increment();
            }
        });
    }

    @Override
    public void service(
            HttpServletRequest req, HttpServletResponse resp) throws Exception {

        resp.setContentType("text/html");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.toString());
        PrintWriter out = resp.getWriter();
        out.println("<html>");

        String type = req.getParameter("type");
        if ("httpheader".equals(type)) {
            resp.setHeader("Link", "<" + req.getRequestURL()
                    + ">; rel=\"canonical\"");
            out.println("<body><p>Canonical URL in HTTP header.</p>");
        } else if ("linkrel".equals(type)) {
            out.println("<head>");
            out.println("<link rel=\"canonical\" ");
            out.println("href=\"" + req.getRequestURL() + "\" />");
            out.println("</head>");
            out.println("<body><p>Canonical URL in HTML &lt;head&gt;.</p>");
        } else {
            out.println("<body><p>Canonical page</p>");
        }
        out.println(
                "<h1>Handling of (non)canonical URLs</h1>"
              + "<p>The links below are pointing to pages that should "
              + "be considered copies of this page when accessed "
              + "without URL parameters.</p>"
              + "<ul>"
              + "<li><a href=\"" + req.getRequestURL() + "?type=httpheader\">"
              + "HTTP Header</a></li>"
              + "<li><a href=\"" + req.getRequestURL() + "?type=linkrel\">"
              + "link rel</a></li>"
              + "</ul>"
              + "</body>"
              + "</html>");
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        assertListSize("document", committer.getAddOperations(), 1);
        Assertions.assertEquals(
                2, canCount.intValue(),
                "Wrong number of canonical link rejection.");
    }
}
