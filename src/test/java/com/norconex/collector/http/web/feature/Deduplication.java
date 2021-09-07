/* Copyright 2021 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Test detection of duplicate files within crawling session.
 * @author Pascal Essiembre
 */
public class Deduplication extends AbstractTestFeature {

    @Override
    public int numberOfRun() {
        return 1;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        cfg.setMetadataChecksummer(new LastModifiedMetadataChecksummer());
        cfg.setDocumentChecksummer(new MD5DocumentChecksummer());
        cfg.setMetadataDeduplicate(true);
        cfg.setDocumentDeduplicate(true);
        cfg.setNumThreads(1);
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {

        // Main page: OK
        // Page 1: OK
        // Page 2: should be meta-duplicated
        // Page 3: should be content-duplicated

        String page = req.getParameter("page");
        // 2001-01-01T01:01:01 GMT
        long staticDate = 978310861000l;

        String page1and3Content = "<h1>Dedup test page 1 and 3</h1>\n"
                + "<p>Page 1 and 3 share this same content."
                + "Page 1 being encountered first, it won't be flagged as "
                + "duplicate, but page 3 will, based on content checksum.</p>";

        if (StringUtils.isBlank(page)) {
            out.println("<h1>Deduplicate files test main page</h1>");
            out.println("<p>The 3 links below point to pages that are: "
                + "<ul>"
                + "<li><a href=\"?page=1\">"
                + "Link 1: Not a duplicate.</li>"
                + "<li><a href=\"?page=2\">"
                + "Link 2: Duplicate of link 1 by metadata checksum."
                + "</li>"
                + "<li><a href=\"?page=3\">"
                + "Link 3: Duplicate of link 1 by content checksum."
                + "</li>"
                + "</ul>");
        } else if ("1".equals(page)) {
            resp.setDateHeader("Last-Modified", staticDate);
            out.println(page1and3Content);
        } else if ("2".equals(page)) {
            // same modified date as page 1.
            resp.setDateHeader("Last-Modified", staticDate);
            out.println("<h1>Dedup test page 2</h1>");
            out.println("<p>This page as the same Last-Modified HTTP response "
                    + "value is as page 1. It should be considered as "
                    + "a duplicate based on metadata checksum.</p>");
        } else if ("3".equals(page)) {
            resp.setDateHeader("Last-Modified", System.currentTimeMillis());
            out.println(page1and3Content);
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        List<UpsertRequest> upserts = committer.getUpsertRequests();

        assertListSize("document", upserts, 2);

        Assertions.assertTrue(upserts.stream().anyMatch(
                up -> up.getReference().endsWith("Deduplication")));
        Assertions.assertTrue(upserts.stream().anyMatch(
                up -> up.getReference().endsWith("Deduplication?page=1")));
    }
}
