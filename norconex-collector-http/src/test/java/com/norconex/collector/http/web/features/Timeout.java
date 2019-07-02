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

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.Sleeper;

/**
 * Test proper handling of page timeouts.
 * @author Pascal Essiembre
 */
// Test for https://github.com/Norconex/collector-http/issues/316
public class Timeout extends AbstractTestFeature {

    @Override
    public int numberOfRun() {
        return 2;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg)
            throws Exception {
        cfg.setDocumentChecksummer(new MD5DocumentChecksummer());

        GenericHttpFetcher f = new GenericHttpFetcher();
        f.getConfig().setConnectionTimeout(2000);
        f.getConfig().setSocketTimeout(2000);
        f.getConfig().setConnectionRequestTimeout(2000);
        cfg.setHttpFetchers(f);
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, PrintWriter out) throws Exception {
        String page = req.getParameter("page");

        if (StringUtils.isBlank(page)) {
            if (isSecondRun()) {
                Sleeper.sleepSeconds(10);
            }
            out.println("<h1>Timeout test main page</h1>");
            out.println("<p>If provided with a 'token' parameter, this "
                + "page takes 10 seconds to return to test "
                + "timeouts, the 2 links below should return right away "
                + "and have a modified content each time accessed : "
                + "<ul>"
                + "<li><a href=\"" + req.getRequestURL() + "?page=1\">"
                + "Timeout child page 1</a></li>"
                + "<li><a href=\"" + req.getRequestURL() + "?page=2\">"
                + "Timeout child page 2</a></li>"
                + "</ul>");
        } else {
            Sleeper.sleepMillis(10);
            out.println("<h1>Timeout test child page " + page + "</h1>");
            out.println("<p>This page content is never the same.</p>"
                    + "<p>Salt: " + System.currentTimeMillis() + "</p><p>"
                    + "Contrary to main page, it should return right "
                    + "away</p>");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        if (isFirstRun()) {
            // Test once and make sure we get 3 additions in total.
            assertListSize("document", committer.getAddOperations(), 3);
        } else if (isSecondRun()) {
            // Test twice and make sure we get 2 modified child docs even if
            // parent times out (as opposed to consider child as orphans to be
            // deleted.
            assertListSize("document", committer.getAddOperations(), 2);
        }
    }
}
