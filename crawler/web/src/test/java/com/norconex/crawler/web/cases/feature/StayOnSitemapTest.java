/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.cases.feature;

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.mutable.MutableObject;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.committer.core.CommitterException;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.commands.crawl.task.operations.scope.impl.GenericUrlScopeResolver;
import com.norconex.crawler.web.commands.crawl.task.operations.sitemap.impl.GenericSitemapLocator;
import com.norconex.crawler.web.commands.crawl.task.operations.sitemap.impl.GenericSitemapResolver;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpFetchResponse;
import com.norconex.crawler.web.fetch.impl.httpclient.HttpClientFetcher;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;
import com.norconex.crawler.web.util.Web;

/**
 * Test that crawling does not go being current sitemap. That is, it should
 * not queue URLs from pages, and certainly not follow them.
 */
@MockServerSettings
class StayOnSitemapTest {

    private final String sitemapPath = "/sitemap.xml";
    private final String page1Path = "/mysite/page1.html";
    private final String page2Path = "/mysite/page2.html";
    private final String page3Path = "/mysite/page3.html";
    private final String page4Path = "/mysite/page4.html";
    private final String page5Path = "/mysite/page5.html";
    private final String page10Path = "/mysite/page10.html";
    private final String page11Path = "/mysite/page11.html";
    private final String page12Path = "/mysite/page12.html";

    private static final String SITEMAP_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE xml>
            <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
               <url><loc>%s</loc></url>
               <url><loc>%s</loc></url>
               <url><loc>%s</loc></url>
               <url><loc>%s</loc></url>
               <url><loc>%s</loc></url>
            </urlset>
            """;

    @WebCrawlTest
    void testStayOnSitemap(ClientAndServer client, WebCrawlerConfig cfg)
            throws CommitterException {
        var exception = new MutableObject<Exception>();
        var referrers = new ArrayList<String>();

        cfg.setStartReferences(List.of(serverUrl(client, page1Path)));
        cfg
                .setSitemapResolver(new GenericSitemapResolver())
                .setSitemapLocator(new GenericSitemapLocator())
                .setMaxDepth(3)
                // custom fetcher that stores exception (last one)
                // and referrers
                .setFetchers(List.of(new HttpClientFetcher() {
                    @Override
                    public HttpFetchResponse fetch(HttpFetchRequest req)
                            throws FetchException {
                        try {
                            Optional.ofNullable(Web.docContext(
                                    req.getDoc())
                                    .getReferrerReference())
                                    .ifPresent(referrers::add);
                            return super.fetch(req);
                        } catch (FetchException | RuntimeException e) {
                            exception.setValue(e);
                            throw e;
                        }
                    }
                }));
        ((GenericUrlScopeResolver) cfg.getUrlScopeResolver())
                .getConfiguration().setStayOnSitemap(true);

        mockServer(client);

        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        // There should be no exception, else, it likely means it tried
        // to fetch an external URL.
        assertThat(exception.getValue()).isNull();

        // There should be no refferers in fetch attempts, as as they should
        // all coming from sitemaps
        assertThat(referrers).isEmpty();

        // 5 documents should have been committed in total (discovered links
        // being ignored)
        assertThat(mem.getRequestCount()).isEqualTo(5);
        assertThat(mem.getDeleteCount()).isZero();

        // All links must have a depth of zero.
        mem.getUpsertRequests().forEach(req -> {
            assertThat(
                    req.getMetadata().getInteger(
                            "crawler.depth")).isZero();
            assertThat(req.getReference()).containsAnyOf(
                    page1Path,
                    page2Path,
                    page3Path,
                    page4Path,
                    page5Path);
        });

        mem.clean();
    }

    private void mockServer(ClientAndServer client) {
        client.reset();

        // Sitemap
        client
                .when(request(sitemapPath))
                .respond(response()
                        .withBody(
                                SITEMAP_XML.formatted(
                                        serverUrl(client, page1Path),
                                        serverUrl(client, page2Path),
                                        serverUrl(client, page3Path),
                                        serverUrl(client, page4Path),
                                        serverUrl(client, page5Path)),
                                MediaType.XML_UTF_8));

        // The links in ALL following pages should not be followed when
        // sitemap is present and stayOnSitemap is true.

        // PAGE 1: External links only
        MockWebsite.whenHtml(
                client, page1Path,
                """
                <p>These links are external so should not be followed:</p>
                <ul>
                  <li><a href="%s">External A</a></li>
                  <li><a href="%s">External B</a></li>
                </ul>
                """.formatted(
                        "https://badhost.badhost/externalA",
                        "https://badhost.badhost/externalB"));

        // PAGE 2: Internal links only, all in sitemap
        MockWebsite.whenHtml(
                client,
                page2Path,
                """
                <p>These links are internal and on sitemap so
                   should be followed:</p>
                <ul>
                  <li><a href="%s">Internal 1</a></li>
                  <li><a href="%s">Internal 3</a></li>
                  <li><a href="%s">Internal 4</a></li>
                </ul>
                """.formatted(
                        serverUrl(client, page1Path),
                        serverUrl(client, page3Path),
                        serverUrl(client, page4Path)));

        // PAGE 3: Internal links only, NOT in sitemap
        MockWebsite.whenHtml(
                client,
                page3Path,
                """
                <p>These links are internal but not on sitemap, so should
                   NOT be followed:</p>
                <ul>
                  <li><a href="%s">Internal 10</a></li>
                  <li><a href="%s">Internal 11</a></li>
                </ul>
                """.formatted(
                        serverUrl(client, page10Path),
                        serverUrl(client, page11Path)));

        // PAGE 4: Mix of internal/external not on sitemap and on sitemap
        MockWebsite.whenHtml(
                client,
                page4Path,
                """
                <p>This page has different types of links, only some should
                   be followed:</p>
                <ul>
                  <li><a href="%s">External C</a></li>
                  <li><a href="%s">Internal 4</a></li>
                  <li><a href="%s">Internal 12</a></li>
                </ul>
                """.formatted(
                        "https://badhost.badhost/externalC",
                        serverUrl(client, page4Path),
                        serverUrl(client, page12Path)));

        // PAGE 5: This page is sitemap only, not otherwise referenced.
        MockWebsite.whenHtml(client, page5Path, """
                <p>This page can only be found via sitemap.</p>
                """);
    }
}
