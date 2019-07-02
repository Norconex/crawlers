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
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.Sleeper;

/**
 * Test detection of modified files.
 * @author Pascal Essiembre
 */
public class ModifiedFiles extends AbstractTestFeature {

    @Override
    public int numberOfRun() {
        return 2;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        cfg.setMetadataChecksummer(new LastModifiedMetadataChecksummer());
        cfg.setDocumentChecksummer(new MD5DocumentChecksummer());
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {
        String page = req.getParameter("page");
        // 2001-01-01T01:01:01 GMT
        long staticDate = 978310861000l;
        if (StringUtils.isBlank(page)) {
            resp.setDateHeader("Last-Modified", staticDate);
            out.println("<h1>Modified files test main page</h1>");
            out.println("<p>While this page is never modified, the 3 links "
                + "below point to pages that are: "
                + "<ul>"
                + "<li><a href=\"?page=1\">"
                + "Modified Test Page 1</a>: Ever changing Last-Modified "
                + "date in http header.</li>"
                + "<li><a href=\"?page=2\">"
                + "Modified Test Page 2</a>: Ever changing body content."
                + "</li>"
                + "<li><a href=\"?page=3\">"
                + "Modified Test Page 3</a>: Both header and body are "
                + "ever changing.</li>"
                + "</ul>");
        } else if ("1".equals(page)) {
            // Wait 1 second to make sure the date has changed.
            Sleeper.sleepSeconds(1);
            resp.setDateHeader("Last-Modified", System.currentTimeMillis());
            out.println("<h1>Modified test page 1 (meta)</h1>");
            out.println("<p>This page content is always the same, but "
                    + "the Last-Modified HTTP response value is always "
                    + "the current date (so it keeps changing).</p>");
        } else if ("2".equals(page)) {
            resp.setDateHeader("Last-Modified", staticDate);
            out.println("<h1>Modified test page 2 (content)</h1>");
            out.println("<p>This page content always changes because of "
                    + "this random number: " + System.currentTimeMillis()
                    + "</p><p>The Last-Modified HTTP response value is "
                    + "always the same.</p>");
        } else if ("3".equals(page)) {
            // Wait 1 second to make sure the date has changed.
            Sleeper.sleepSeconds(1);
            resp.setDateHeader("Last-Modified", System.currentTimeMillis());
            out.println("<h1>Modified test page 3 (meta + content)</h1>");
            out.println("<p>This page content always changes because of "
                    + "this random number: " + System.currentTimeMillis()
                    + "</p><p>The Last-Modified HTTP response value is "
                    + "always the current date (so it keeps changing)."
                    + "</p>");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        if (isFirstRun()) {
            // Test once and make sure we get 4 additions in total.
            assertListSize("document", committer.getAddOperations(), 4);
        } else if (isSecondRun()) {
            // Test twice and make sure we get 1 add (3 unmodified), because:
            // Page 1 has new modified date, we check content. Content is same.
            // Page 2 has same modified date, we do not go further
            //        (ignore content).
            // Page 3 has new modified date, so we check content.
            // Content is modified.
            assertListSize("document", committer.getAddOperations(), 1);
        }
    }
}
