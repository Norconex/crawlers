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

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Tests that the ETag "If-None-Match" is supported properly.
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/ETag
 * https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/If-None-Match
 *
 * Crawled 3 times:
 *
 *  - 1. Clean crawl, gets and cache the Etag, 1 doc committed
 *  - 2. Server side not modified, so should be unmodified, 0 doc committed
 *  - 3. Server side modified, etag disable on fetcher, so it thinks
 *          it's new because there are no other checks, so 1 doc committed.
 *  - 4. Server side modified, etag enabled on fetcher, 1 doc committed.
 *
 * @author Pascal Essiembre
 */
//Test for https://github.com/Norconex/collector-http/issues/182
public class IfNoneMatch extends AbstractInfiniteDepthTestFeature {

    private static final Logger LOG =
            LoggerFactory.getLogger(IfNoneMatch.class);

    private String serverETag =
            RandomStringUtils.random(40, true, true);

    @Override
    public int numberOfRun() {
        return 4;
    }

    @Override
    protected void doHtmlService(
            HttpServletRequest req, HttpServletResponse resp, PrintWriter out)
                    throws Exception {

        // we only set new etags on 3rd and 4th runs
        if (isThirdRun() || isFourthRun()) {
            serverETag = RandomStringUtils.random(40, true, true);
        }

        resp.setHeader(HttpHeaders.ETAG, serverETag);

        String clientETag = req.getHeader(HttpHeaders.IF_NONE_MATCH);
        LOG.debug("Server ETag: " + serverETag);
        LOG.debug("Client ETag: " + clientETag);
        if (serverETag.equals(clientETag)) {
            resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
        }
        super.doHtmlService(req, resp, out);
    }


    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {
        cfg.setMaxDepth(0);

        // disable ETag client support on 3 run
        if (isThirdRun()) {
            ((GenericHttpFetcher) cfg.getHttpFetchers().get(0))
                    .getConfig().setDisableETag(true);
        }
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        // Only first and fourth attempt should have 1 doc
        if (isSecondRun()) {
            assertListSize("document", committer.getUpsertRequests(), 0);
        } else {
            assertListSize("document", committer.getUpsertRequests(), 1);
        }
    }
}
