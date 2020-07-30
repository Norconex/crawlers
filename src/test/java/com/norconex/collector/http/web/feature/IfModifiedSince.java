/* Copyright 2020 Norconex Inc.
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

import static org.apache.http.HttpHeaders.IF_MODIFIED_SINCE;
import static org.apache.http.HttpHeaders.LAST_MODIFIED;

import java.io.PrintWriter;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Tests that the "If-Modified-Since" is supported properly.
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-Modified-Since
 *
 * Tests the page 4 times:
 *
 *   Request 1: page last modified date is 5 days ago.
 *   Request 2: page last modified date is same as request 1.
 *   Request 3: page last modified date is now.
 *   Request 4: page last modified date is same as request 3.
 *
 * On the first and third attempt only shall we get documents committed.
 * Other attempts should be unmodified.
 * @author Pascal Essiembre
 */
//Test for https://github.com/Norconex/collector-http/issues/637
public class IfModifiedSince extends AbstractInfiniteDepthTestFeature {

    private static final Logger LOG =
            LoggerFactory.getLogger(IfModifiedSince.class);

    private long serverLastModified = dateDaysFromNow(5);
    private int requestCount;


    @Override
    protected void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {

        requestCount++;

        resp.addDateHeader(LAST_MODIFIED, serverLastModified);
        if (LOG.isDebugEnabled()) {
            LOG.debug("Requested If-Modified-Since: {}",
                    req.getHeader(IF_MODIFIED_SINCE));
            LOG.debug("     Response Last-Modified: {}",
                    resp.getHeader(LAST_MODIFIED));
        }

        if (requestCount == 3) {
            serverLastModified = dateDaysFromNow(0);
        }

        long clientLastModified = req.getDateHeader(IF_MODIFIED_SINCE);
        if (clientLastModified != -1
                && serverLastModified <= clientLastModified) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        super.doHtmlService(req, resp, out);
    }

    private long dateDaysFromNow(int days) {
        return ZonedDateTime.now(ZoneId.of("GMT")).minusDays(days).withNano(0)
                .toInstant().toEpochMilli();
    }

    @Override
    public int numberOfRun() {
        return 4;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        cfg.setMaxDepth(0);
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        // Only first and third attempt should have 1 doc
        if (isFirstRun() || isThirdRun()) {
            assertListSize("document", committer.getUpsertRequests(), 1);
        } else {
            assertListSize("document", committer.getUpsertRequests(), 0);
        }
    }
}
