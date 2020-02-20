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

import static com.norconex.collector.http.doc.HttpDocMetadata.REDIRECT_TRAIL;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.IAddOperation;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * The tail of redirects should be kept as metadata so implementors
 * can know where documents came from. This test starts with
 * a canonical redirect.
 * @author Pascal Essiembre
 */
public class CanonicalRedirectLoop extends AbstractTestFeature {

    private static final Logger LOG =
            LoggerFactory.getLogger(CanonicalRedirectLoop.class);

    protected final MutableInt count = new MutableInt();

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg)
            throws Exception {
        count.setValue(0);
        cfg.setIgnoreCanonicalLinks(false);

        if (isFirstRun()) {
            LOG.debug("Testing canonical.");
            cfg.setStartURLs(cfg.getStartURLs().get(0) + "?type=canonical");
        } else {
            LOG.debug("Testing redirect.");
            cfg.setStartURLs(cfg.getStartURLs().get(0) + "?type=redirect");
        }
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {

        if (count.intValue() == 10) {
            resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                    "Too many canonicals + redirects (loop).");
            count.setValue(0);
            return;
        }

        count.increment();
        String type = req.getParameter("type");

        String baseURL = req.getRequestURL().toString();

        if ("canonical".equals(type)) {
            LOG.debug("Canonical requested, which points to redirect.");
            resp.setHeader("Link",
                    "<" + baseURL + "?type=redirect>; rel=\"canonical\"");
            out.println("<h1>Canonical-redirect circular reference.</h1>"
                    + "<p>This page has a canonical URL in the HTTP header "
                    + "that points to a page that redirects back to this "
                    + "one (loop). The crawler should be smart enough "
                    + "to pick one and not enter in an infite loop.</p>");
        } else {
            LOG.debug("Redirect requested, which points to canonical.");
            resp.sendRedirect(baseURL + "?type=canonical");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        assertListSize("document", committer.getAddOperations(), 1);

        IAddOperation doc = committer.getAddOperations().get(0);
        String content = content(doc);

        assertTrue(
                content.contains("Canonical-redirect circular reference"),
                "Wrong content");
        assertTrue(
                doc.getReference().contains("?type=canonical"),
                "Wrong reference");

        LOG.debug("Final reference: {}", doc.getReference());
        LOG.debug("Final trail: {}",
                doc.getMetadata().getStrings(REDIRECT_TRAIL));
    }
}
