/* Copyright 2025 Norconex Inc.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.text.StringSubstitutor;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.ResourceLoader;

// Test for https://github.com/Norconex/crawlers/issues/1121
// Redirected document targets should not be deleted if not ready to be
// recrawled
public class NonRecrawlablesRedirected extends AbstractTestFeature {

    @Override
    public int numberOfRun() {
        return 4;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) {
        cfg.setStartSitemapURLs(cfg.getStartURLs());
        cfg.setStartURLs((List<String>) null);
        cfg.setOrphansStrategy(HttpCrawlerConfig.OrphansStrategy.DELETE);
    }

    @Override
    public void service(HttpServletRequest req, HttpServletResponse resp)
            throws Exception {

        var page = NumberUtils.toInt(req.getParameter("page"), -1);

        // if page param is blank, the request is for the sitemap
        if (page == -1) {
            var baseLocURL = req.getRequestURL() + "?page=";
            Map<String, String> vars = new HashMap<>();
            vars.put("loc1", baseLocURL + 1);
            vars.put("loc2", baseLocURL + 2);
            vars.put("loc3", baseLocURL + 3);
            vars.put("loc4", baseLocURL + 4);
            vars.put("loc400", baseLocURL + 400);
            var xml = ResourceLoader.getXmlString(getClass());
            xml = StringSubstitutor.replace(xml, vars);
            resp.setContentType("application/xml");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().println(xml);
        } else if (page <= 4) { // redirect to another page (pageNo x 100).
            resp.sendRedirect(req.getRequestURL() + "?page=" + (page * 100));
        } else  {
            resp.setContentType("text/html");
            resp.setCharacterEncoding("UTF-8");
            var out = resp.getWriter();
            out.println("<h1>Page " + page + "</h1>");
            out.println("<p>This is page " + page + ".</p>");
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        if (isFirstRun()) {
            // Run 1: we should get 4 additions in total.
            assertListSize("added", committer.getUpsertRequests(), 4);
        } else {
            // Of the redirect targets, only "400" is in the sitemap with
            // a last modified date so only it is non recrawlable.
            // We should get 3 additions (100, 200, 300) 0 deletions on
            // subsequent runs
            assertListSize("added", committer.getUpsertRequests(), 3);
            Assertions.assertTrue(committer
                    .getUpsertRequests()
                    .stream()
                    .anyMatch(req -> req.getReference().endsWith("100")),
                    "Should contain ...100");
            Assertions.assertTrue(committer
                    .getUpsertRequests()
                    .stream()
                    .anyMatch(req -> req.getReference().endsWith("200")),
                    "Should contain ...200");
            Assertions.assertTrue(committer
                    .getUpsertRequests()
                    .stream()
                    .anyMatch(req -> req.getReference().endsWith("300")),
                    "Should contain ...300");
        }
        assertListSize("deleted", committer.getDeleteRequests(), 0);
    }
}
