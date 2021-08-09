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

import static com.norconex.collector.http.fetch.HttpMethod.GET;
import static com.norconex.collector.http.fetch.HttpMethod.HEAD;

import java.io.IOException;
import java.util.Arrays;

import org.apache.tools.ant.filters.StringInputStream;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.core.doc.CrawlDoc;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfig.HttpMethodSupport;
import com.norconex.collector.http.fetch.HttpFetchException;
import com.norconex.collector.http.fetch.HttpMethod;
import com.norconex.collector.http.fetch.IHttpFetchResponse;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcherConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.UpsertRequest;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Tests that a page is only fetched by the fetcher we are interested in.
 * @author Pascal Essiembre
 */
//Related to https://github.com/Norconex/collector-http/issues/654
public class HttpFetcherAccept extends AbstractInfiniteDepthTestFeature {

    @Override
    public int numberOfRun() {
        return 7;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg) throws Exception {

        cfg.setMaxDepth(0);

        GenericHttpFetcher headFetcher = createFetcher(HEAD);
        GenericHttpFetcher getFetcher = createFetcher(GET);


        // First run we are not fetching HTTP HEAD so the result should come
        // from GET fetcher only.

        // Second run we are fetching HTTP HEAD and we want to proceed
        // successfully with just that, not invoking GET.

        switch (getRunCount()) {
        case 1:
            // Run 1: HEAD: disabled; GET: required-success (default config).
            // Expected: Response from GET only.
            cfg.setFetchHttpHead(HttpMethodSupport.DISABLED);
            cfg.setFetchHttpGet(HttpMethodSupport.REQUIRED);
            break;
        case 2:
            // Run 2: HEAD: required-success; GET: disabled.
            // Expected: Response from HEAD only.
            cfg.setFetchHttpHead(HttpMethodSupport.REQUIRED);
            cfg.setFetchHttpGet(HttpMethodSupport.DISABLED);
            break;
        case 3:
            // Run 3: HEAD: required-success; GET: required-success.
            // Expected: Response from both GET and HEAD only, with GET content
            cfg.setFetchHttpHead(HttpMethodSupport.REQUIRED);
            cfg.setFetchHttpGet(HttpMethodSupport.REQUIRED);
            break;
        case 4:
            // Run 4: HEAD: required-fail; GET: optional-success.
            // Expected: No response
            cfg.setFetchHttpHead(HttpMethodSupport.REQUIRED);
            headFetcher.setReferenceFilters(ref -> false);
            cfg.setFetchHttpGet(HttpMethodSupport.OPTIONAL);
            break;
        case 5:
            // Run 5: HEAD: required-success; GET: required-fail.
            // Expected: No response.
            cfg.setFetchHttpHead(HttpMethodSupport.REQUIRED);
            cfg.setFetchHttpGet(HttpMethodSupport.REQUIRED);
            getFetcher.setReferenceFilters(ref -> false);
            break;
        case 6:
            // Run 6: HEAD: optional-fail, GET: optional-success.
            // Expected: Response from GET.
            cfg.setFetchHttpHead(HttpMethodSupport.OPTIONAL);
            headFetcher.setReferenceFilters(ref -> false);
            cfg.setFetchHttpGet(HttpMethodSupport.OPTIONAL);
            break;
        case 7:
            // Run 7: HEAD: optional-success, GET: optional-fail.
            // Expected: Response from HEAD.
            cfg.setFetchHttpHead(HttpMethodSupport.OPTIONAL);
            cfg.setFetchHttpGet(HttpMethodSupport.OPTIONAL);
            getFetcher.setReferenceFilters(ref -> false);
            break;
        default:
            break;
        }

        cfg.setHttpFetchers(headFetcher, getFetcher);
    }

    private GenericHttpFetcher createFetcher(HttpMethod method) {
        GenericHttpFetcherConfig getConfig = new GenericHttpFetcherConfig();
        getConfig.setHttpMethods(Arrays.asList(method));
        GenericHttpFetcher fetcher = new GenericHttpFetcher(getConfig) {
            @Override
            public IHttpFetchResponse fetch(CrawlDoc doc,
                    HttpMethod httpMethod) throws HttpFetchException {
                IHttpFetchResponse r = super.fetch(doc, httpMethod);
                doc.setInputStream(new StringInputStream("I am " + method));
                doc.getMetadata().add("whatAmI", method.toString());
                return r;
            }
        };
        return fetcher;
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        UpsertRequest req = null;

        switch (getRunCount()) {
        case 1:
            // Run 1: HEAD: disabled; GET: required-success (default config).
            // Expected: Response from GET only.
            req = assertListSizeAndGetFirst(committer, 1);
            assertContent(req, GET);
            assertMeta(req, GET);
            break;
        case 2:
            // Run 2: HEAD: required-success; GET: disabled.
            // Expected: Response from HEAD only.
            req = assertListSizeAndGetFirst(committer, 1);
            assertContent(req, HEAD);
            assertMeta(req, HEAD);
            break;
        case 3:
            // Run 3: HEAD: required-success; GET: required-success.
            // Expected: Response from both GET and HEAD meta, with GET content
            req = assertListSizeAndGetFirst(committer, 1);
            assertContent(req, GET);
            assertMeta(req, HEAD, GET);
            break;
        case 4:
            // Run 4: HEAD: required-fail; GET: optional-success.
            // Expected: No response
            req = assertListSizeAndGetFirst(committer, 0);
            Assertions.assertNull(req);
            break;
        case 5:
            // Run 5: HEAD: required-success; GET: required-fail.
            // Expected: No response.
            req = assertListSizeAndGetFirst(committer, 0);
            Assertions.assertNull(req);
            break;
        case 6:
            // Run 6: HEAD: optional-fail, GET: optional-success.
            // Expected: Response from GET.
            req = assertListSizeAndGetFirst(committer, 1);
            assertContent(req, GET);
            assertMeta(req, GET);
            break;
        case 7:
            // Run 7: HEAD: optional-success, GET: optional-fail.
            // Expected: Response from HEAD.
            req = assertListSizeAndGetFirst(committer, 1);
            assertContent(req, HEAD);
            assertMeta(req, HEAD);
            break;
        default:
            break;
        }
    }

    private UpsertRequest assertListSizeAndGetFirst(
            MemoryCommitter committer, int size) {
        assertListSize("document", committer.getUpsertRequests(), size);
        if (size > 0) {
            return committer.getUpsertRequests().get(0);
        }
        return null;
    }
    private void assertContent(
            UpsertRequest req, HttpMethod method) throws IOException {
        Assertions.assertEquals("I am " + method, content(req));
    }
    private void assertMeta(
            UpsertRequest req, HttpMethod... methods) throws IOException {
        Assertions.assertEquals(methods.length,
                req.getMetadata().getStrings("whatAmI").size());
        Assertions.assertEquals(Arrays.asList(methods),
                req.getMetadata().getList("whatAmI", HttpMethod.class));
    }
}
