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

import java.io.InputStream;
import java.io.PrintWriter;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.core.crawler.event.impl.DeleteRejectedEventListener;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.DeleteRequest;
import com.norconex.committer.core3.ICommitterRequest;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.IDocumentFilter;
import com.norconex.importer.parser.ParseState;

/**
 * Test the deletion of rejected references with
 * {@link DeleteRejectedEventListener}.
 * @author Pascal Essiembre
 */
public class RejectedRefsDeletion extends AbstractTestFeature {

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        DeleteRejectedEventListener drel = new DeleteRejectedEventListener();
        drel.setEventMatcher(TextMatcher.csv(
                "REJECTED_NOTFOUND, REJECTED_BAD_STATUS"));
        cfg.addEventListeners(drel);
        cfg.getImporterConfig().setPreParseConsumer(
                HandlerConsumer.fromHandlers(new IDocumentFilter() {
                    @Override
                    public boolean acceptDocument(HandlerDoc doc,
                            InputStream input, ParseState parseState)
                            throws ImporterHandlerException {
                        return !doc.getReference().endsWith("page=6");
                    }
                })
        );
        cfg.setNumThreads(1);
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {

        // Page 0: OK (Main page: Link index)
        // Page 1: OK
        // Page 2: REJECTED_NOTFOUND   --> delete #1
        // Page 3: OK
        // Page 4: REJECTED_BAD_STATUS --> delete #2
        // Page 5: REJECTED_NOTFOUND   --> delete #3
        // Page 6: REJECTED_IMPORT     --> no delete, event does not match
        // Page 2: REJECTED_NOTFOUND   --> no delete, page 2 duplicate
        // Page 7: REJECTED_NOTFOUND   --> delete #4

        // Expected: 3 upserts, 4 deletes (3 not found and 1 bad status)

        String page = req.getParameter("page");

        if (StringUtils.isBlank(page)) {
            out.println("<h1>DeleteRejectedEventListener test main page</h1>");
            out.println("<p>The test pages and expected events generated:</p>"
                + "<ol>"
                + "<li><a href=\"?page=1\">OK</li>"
                + "<li><a href=\"?page=2\">REJECTED_NOTFOUND</li>"
                + "<li><a href=\"?page=3\">OK</li>"
                + "<li><a href=\"?page=4\">REJECTED_BAD_STATUS</li>"
                + "<li><a href=\"?page=5\">REJECTED_NOTFOUND</li>"
                + "<li><a href=\"?page=6\">REJECTED_IMPORT</li>"
                + "<li><a href=\"?page=2\">REJECTED_NOTFOUND (p.2 dupl.)</li>"
                + "<li><a href=\"?page=7\">REJECTED_NOTFOUND</li>"
                + "</ol>");
        } else if (page.equals("1")) {
            out.println("Page 1 expected event: OK");
        } else if (page.equals("2")) {
            resp.sendError(HttpStatus.SC_NOT_FOUND);
            out.println("Page 2 expected event: REJECTED_NOTFOUND");
        } else if (page.equals("3")) {
            out.println("Page 3 expected event: OK");
        } else if (page.equals("4")) {
            resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR);
            out.println("Page 4 expected event: REJECTED_BAD_STATUS");
        } else if (page.equals("5")) {
            resp.sendError(HttpStatus.SC_NOT_FOUND);
            out.println("Page 5 expected event: REJECTED_NOTFOUND");
        } else if (page.equals("6")) {
            out.println("Page 6 expected event: REJECTED_IMPORT");
        } else if (page.equals("7")) {
            resp.sendError(HttpStatus.SC_NOT_FOUND);
            out.println("Page 7 expected event: REJECTED_NOTFOUND");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        // Expected: 3 upserts, 4 deletes (3 not found and 1 bad status)
        List<UpsertRequest> upserts = committer.getUpsertRequests();
        List<DeleteRequest> deletes = committer.getDeleteRequests();

        assertListSize("upserts", upserts, 3);
        assertListSize("deletes", deletes, 4);

        assertHasPage(upserts, 1);
        assertHasPage(upserts, 3);
        assertHasPage(deletes, 2);
        assertHasPage(deletes, 4);
        assertHasPage(deletes, 5);
        assertHasPage(deletes, 7);
    }

    private void assertHasPage(
            List<? extends ICommitterRequest> requests, int page) {
        Assertions.assertTrue(requests.stream()
                .anyMatch(r -> r.getReference().endsWith("page=" + page)),
                        "Page " + page);
    }
}
