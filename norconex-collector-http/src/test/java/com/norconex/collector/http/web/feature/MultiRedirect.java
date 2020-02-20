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

import org.apache.commons.lang3.math.NumberUtils;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.doc.HttpDocMetadata;
import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.IAddOperation;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * The tail of redirects should be kept as metadata so implementors
 * can know where documents came from.
 * @author Pascal Essiembre
 */
public class MultiRedirect extends AbstractTestFeature {

    private static final Logger LOG =
            LoggerFactory.getLogger(MultiRedirect.class);

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig crawlerConfig)
            throws Exception {
        crawlerConfig.setMaxDepth(0);
    }

    @Override
    public void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {

        int maxRedirects = 5;
        int count = NumberUtils.toInt(req.getParameter("count"), 0);

        if (count < maxRedirects) {
            resp.sendRedirect(req.getRequestURL()
                    + "/redirected/page.html?count=" + (count + 1));
            return;
        }
        out.println("<h1>Multi-redirects test page</h1>");
        out.println("The URL was redirected " + maxRedirects + " times. "
                + "Was the redirect trail kept somehwere in your crawler?");
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        assertListSize("document", committer.getAddOperations(), 1);

        IAddOperation doc = committer.getAddOperations().get(0);
        String ref = doc.getReference();

        List<String> trail = doc.getMetadata().getStrings(
                HttpDocMetadata.REDIRECT_TRAIL);
        LOG.debug("Redirect source URLs:" + trail);
        assertListSize("URL", trail, 5);

        // Test the trail order:
        Assertions.assertFalse(trail.get(0).contains("count"));
        Assertions.assertTrue(trail.get(1).contains("count=1"));
        Assertions.assertTrue(trail.get(2).contains("count=2"));
        Assertions.assertTrue(trail.get(3).contains("count=3"));
        Assertions.assertTrue(trail.get(4).contains("count=4"));

        // Test final URL:
        Assertions.assertTrue( ref.contains("count=5"),
                "Invalid redirection URL: " + ref);
    }
}
