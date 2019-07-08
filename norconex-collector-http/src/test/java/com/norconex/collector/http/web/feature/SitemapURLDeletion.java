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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StringSubstitutor;
import org.apache.http.HttpStatus;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * The second time the sitemap has 1 less URL and that URL no longer
 * exists.
 * @author Pascal Essiembre
 */
//Test for https://github.com/Norconex/collector-http/issues/390
public class SitemapURLDeletion extends AbstractTestFeature {

    @Override
    public int numberOfRun() {
        return 2;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg)
            throws Exception {
        cfg.setStartSitemapURLs(cfg.getStartURLs());
        cfg.setStartURLs((List<String>) null);
        cfg.setOrphansStrategy(HttpCrawlerConfig.OrphansStrategy.PROCESS);
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {

        int page = NumberUtils.toInt(req.getParameter("page"), -1);

        // if page is blank, the request is for the sitemap
        if (page == -1) {
            String baseLocURL = req.getRequestURL() + "?&amp;page=";
            Map<String, String> vars = new HashMap<>();
            vars.put("loc1", baseLocURL + 1);
            vars.put("loc2", baseLocURL + 2);
            if (isFirstRun()) {
                vars.put("loc3", baseLocURL + 3);
            } else {
                vars.put("loc3", baseLocURL + 33);
            }
            String xml = IOUtils.toString(getClass().getResourceAsStream(
                    getClass().getSimpleName() + ".xml"), UTF_8);
            xml = StringSubstitutor.replace(xml, vars);
            resp.setContentType("application/xml");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(xml);
        } else {
            resp.setContentType("text/html");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter out = resp.getWriter();
            if (page < 3) {
                out.println("<h1>Sitemap permanent page " + page + "</h1>");
                out.println("<p>This page should always be there.</p>");
            } else if (page == 3) {
                if (isFirstRun()) {
                    out.println("<h1>Sitemap temp page " + page + "</h1>");
                    out.println("<p>This page should be there the first "
                            + "time the site is crawled only.</p>");
                } else {
                    resp.sendError(HttpStatus.SC_NOT_FOUND,
                            "Not found (so they say)");
                }
            } else if (page == 33) {
                out.println("<h1>Sitemap new page " + page + "</h1>");
                out.println("<p>This page should be there the second "
                        + "time the site is crawled only.</p>");
            }
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        if (isFirstRun()) {
            // Test once and make sure we get 3 additions in total.
            assertListSize("added", committer.getAddOperations(), 3);
            assertListSize("deleted", committer.getDeleteOperations(), 0);
        } else {
            // Test twice and make sure we get 1 add, 2 unmodified and
            // 1 pages deleted, regardless of delay specified in sitemap.
            assertListSize("added", committer.getAddOperations(), 1);
            assertListSize("deleted", committer.getDeleteOperations(), 1);
        }
    }
}
