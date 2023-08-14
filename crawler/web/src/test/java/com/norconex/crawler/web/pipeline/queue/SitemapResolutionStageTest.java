/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web.pipeline.queue;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.junit.jupiter.api.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.MediaType;

import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebsiteMock;
import com.norconex.crawler.web.sitemap.impl.GenericSitemapLocator;
import com.norconex.crawler.web.sitemap.impl.GenericSitemapResolver;

/**
 * Tests sitemap resolution.
 */
@MockServerSettings()
class SitemapResolutionStageTest {

    private static final String SITEMAP_XML = """
            <urlset>
              <url>
                <loc>%s</loc>
                <lastmod>2021-02-26</lastmod>
                <changefreq>daily</changefreq>
                <priority>0.5</priority>
              </url>
              <url>
                <loc>%s</loc>
                <lastmod>2021-04-01</lastmod>
                <changefreq>daily</changefreq>
                <priority>1.0</priority>
              </url>
              <url>
                <loc>%s</loc>
                <lastmod>2021-06-01</lastmod>
                <changefreq>daily</changefreq>
                <priority>1.0</priority>
              </url>
            </urlset>
            """;

    // Should only crawl URLs in sitemap. Start URL not in sitemap.
    @Test
    void testStayOnSitemapWhenPresent(ClientAndServer client) {
        client.reset();
        var baseUrl = serverUrl(client, "");
        var sitemap = SITEMAP_XML.formatted(
                baseUrl + "0001",
                baseUrl + "0002",
                baseUrl + "0003");
       client
            .when(request().withPath("/sitemap.xml"))
            .respond(response().withBody(sitemap, MediaType.XML_UTF_8));
       WebsiteMock.whenInfiniteDepth(client);

        var mem = TestWebCrawlSession
            .forStartReferences(serverUrl(client, "/stayOnSitemap"))
            .crawlerSetup(cfg -> {
                cfg.setNumThreads(1);
                cfg.setMaxDocuments(10);
                cfg.setSitemapLocator(new GenericSitemapLocator());
                cfg.setSitemapResolver(new GenericSitemapResolver());
                cfg.setStayOnSitemapWhenPresent(true);
            })
            .crawl();
        assertThat(mem.getRequestCount()).isEqualTo(3);
        assertThat(mem.getUpsertRequests())
            .map(req -> substringAfterLast(req.getReference(), "/"))
            .containsExactly("0001", "0002", "0003");
    }

    // Should only crawl URLs in sitemap. Start URL included in sitemap.
    @Test
    void testStayOnSitemapWhenPresentStartInSitemap(ClientAndServer client) {
        client.reset();
        var baseUrl = serverUrl(client, "");
        var sitemap = SITEMAP_XML.formatted(
                baseUrl + "0001",
                baseUrl + "0002",
                baseUrl + "0003");
       client
            .when(request().withPath("/sitemap.xml"))
            .respond(response().withBody(sitemap, MediaType.XML_UTF_8));
       WebsiteMock.whenInfiniteDepth(client);

        var mem = TestWebCrawlSession
            .forStartReferences(serverUrl(client, "/0002"))
            .crawlerSetup(cfg -> {
                cfg.setNumThreads(1);
                cfg.setMaxDocuments(10);
                cfg.setSitemapLocator(new GenericSitemapLocator());
                cfg.setSitemapResolver(new GenericSitemapResolver());
                cfg.setStayOnSitemapWhenPresent(true);
            })
            .crawl();
        assertThat(mem.getRequestCount()).isEqualTo(3);
        assertThat(mem.getUpsertRequests())
            .map(req -> substringAfterLast(req.getReference(), "/"))
            .containsExactly("0001", "0002", "0003");
    }

}
