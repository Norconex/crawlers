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
package com.norconex.crawler.web.doc.pipelines.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue.QueueBootstrapContext;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.web.doc.WebCrawlEntry;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.util.Web;

@Timeout(30)
class SitemapEnqueuerTest {

    @WebCrawlTest
    void testEnqueueNoSitemaps(CrawlContext ctx) {
        // No sitemap URLs configured → returns 0, resolver never called
        Web.config(ctx).setStartReferencesSitemaps(List.of());

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var queued = new ArrayList<CrawlEntry>();
        var queueCtx = new QueueBootstrapContext(session, queued::add);

        var count = new SitemapEnqueuer().enqueue(queueCtx);

        assertThat(count).isZero();
        assertThat(queued).isEmpty();
    }

    @WebCrawlTest
    void testEnqueueSitemapsWithNullResolverThrows(CrawlContext ctx) {
        // Sitemap URLs are configured but resolver is null → ConfigurationException
        Web.config(ctx).setStartReferencesSitemaps(
                List.of("http://example.com/sitemap.xml"));
        Web.config(ctx).setSitemapResolver(null);

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var queueCtx = new QueueBootstrapContext(session, e -> {});

        assertThatExceptionOfType(ConfigurationException.class)
                .isThrownBy(() -> new SitemapEnqueuer().enqueue(queueCtx));
    }

    @WebCrawlTest
    void testEnqueueWithResolver(CrawlContext ctx) {
        // Resolver delivers 2 URLs → both queued, count = 2
        var url1 = "http://example.com/page1.html";
        var url2 = "http://example.com/page2.html";

        Web.config(ctx).setStartReferencesSitemaps(
                List.of("http://example.com/sitemap.xml"));
        Web.config(ctx).setSitemapResolver(sitemapCtx -> {
            sitemapCtx.getUrlConsumer().accept(new WebCrawlEntry(url1, 0));
            sitemapCtx.getUrlConsumer().accept(new WebCrawlEntry(url2, 0));
        });

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var queued = new ArrayList<CrawlEntry>();
        var queueCtx = new QueueBootstrapContext(session, queued::add);

        var count = new SitemapEnqueuer().enqueue(queueCtx);

        assertThat(count).isEqualTo(2);
        assertThat(queued).hasSize(2);
    }

    @WebCrawlTest
    void testEnqueueMultipleSitemaps(CrawlContext ctx) {
        // Two sitemap URLs, each delivers 1 URL → total count = 2
        Web.config(ctx).setStartReferencesSitemaps(List.of(
                "http://example.com/sitemap1.xml",
                "http://example.com/sitemap2.xml"));
        Web.config(ctx).setSitemapResolver(sitemapCtx -> {
            // Each sitemap contributes 1 URL derived from its location
            var location = sitemapCtx.getLocation();
            var page = location.replace("sitemap", "page").replace(".xml",
                    ".html");
            sitemapCtx.getUrlConsumer().accept(new WebCrawlEntry(page, 0));
        });

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var queued = new ArrayList<CrawlEntry>();
        var queueCtx = new QueueBootstrapContext(session, queued::add);

        var count = new SitemapEnqueuer().enqueue(queueCtx);

        assertThat(count).isEqualTo(2);
        assertThat(queued).hasSize(2);
    }

    @WebCrawlTest
    void testEnqueueResolverDeliversNoUrls(CrawlContext ctx) {
        // Resolver exists but delivers 0 URLs → count = 0
        Web.config(ctx).setStartReferencesSitemaps(
                List.of("http://example.com/empty-sitemap.xml"));
        Web.config(ctx).setSitemapResolver(sitemapCtx -> {
            // delivers nothing
        });

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(ctx);

        var queued = new ArrayList<CrawlEntry>();
        var queueCtx = new QueueBootstrapContext(session, queued::add);

        var count = new SitemapEnqueuer().enqueue(queueCtx);

        assertThat(count).isZero();
        assertThat(queued).isEmpty();
    }
}
