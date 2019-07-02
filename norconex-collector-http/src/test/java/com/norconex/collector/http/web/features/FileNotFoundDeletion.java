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

import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * Test detection of page deletion (404 - File Not Found).
 * @author Pascal Essiembre
 */
public class FileNotFoundDeletion extends AbstractTestFeature {

    private final List<String> pagesToDelete = new ArrayList<>();

    @Override
    public int numberOfRun() {
        return 3;
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

        if (isNotBlank(page)) {
            if (pagesToDelete.contains(page)) {
                pagesToDelete.remove(page);
                resp.sendError(SC_NOT_FOUND, "Not found (so they say)");
                return;
            } else {
                pagesToDelete.add(page);
            }
        }

        if (StringUtils.isNotBlank(page)) {
            out.println("<h1>Delete test page " + page + "</h1>");
            out.println("<p>This page should give a 404 when accessed "
                    + "a second time with the same token "
                    + "(and keeps toggling on every requests).</p>");
        } else {
            out.println("<h1>Deleted files test main page</h1>");
            out.println("<p>When accessed with a <b>token</b> parameter "
                + "having an arbitrary value, this page will list links "
                + "that are valid the first time they are accessed. "
                + "The second time they are accessed with the same token "
                + "value, the links won't be found and the HTTP "
                + "response will be a 404 when accessing them. "
                + "If you keep accessing the same URLs, the state "
                + "will change on each request from this page to 404.</p>"
                + "<ul>"
                + "<li><a href=\"?page=1\">Delete Test Page 1</a></li>"
                + "<li><a href=\"?page=2\">Delete Test Page 2</a></li>"
                + "<li><a href=\"?page=3\">Delete Test Page 3</a></li>"
                + "</ul>");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        if (isFirstRun()) {
            // First run we should get 4 additions in total.
            assertListSize("additions", committer.getAddOperations(), 4);
            assertListSize("deletions", committer.getDeleteOperations(), 0);
        } else if (isSecondRun()) {
            // Second run we should get 0 add (1 unmodified)
            // and 3 pages to delete.
            assertListSize("additions", committer.getAddOperations(), 0);
            assertListSize("deletions", committer.getDeleteOperations(), 3);
        } else if (isThirdRun()) {
            // Third run we should get 0 deletions
            // and 3 new pages (1 unmodified).
            assertListSize("additions", committer.getAddOperations(), 3);
            assertListSize("deletions", committer.getDeleteOperations(), 0);
        }
    }
}
