/* Copyright 2010-2019 Norconex Inc.
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
package com.norconex.collector.http.sitemap;

import java.util.List;
import java.util.function.Consumer;

import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.fetch.HttpFetchClient;
import com.norconex.collector.http.reference.HttpCrawlReference;


/**
 * <p>
 * Given a URL root, resolve the corresponding sitemap(s), if any, and
 * only if it has not yet been resolved for a crawling session.
 * </p>
 * <p>
 * Sitemaps URLs can be specified as "start URLs" (defined in
 * {@link HttpCrawlerConfig#getStartSitemapURLs()}). It is up to the selected
 * implementation to decide whether to treat sitemaps specified as start URLs
 * any differently.
 * </p>
 * <p>
 * When ignoring sitemap ({@link HttpCrawlerConfig#isIgnoreSitemap()}),
 * the selected sitemap
 * resolver implementation will still be invoked for sitemaps specified as
 * start URLs.
 * </p>
 * <p>
 * Sitemaps locations to resolved may also come from a site
 * <code>robots.txt</code> (provided robots.txt files are not ignored).
 * </p>
 * <p>
 * Is it possible for implementations to not attempt to resolve sitemaps
 * for some URLs.  Refer to specific implementation for more details.
 * </p>
 *
 * @author Pascal Essiembre
 */
public interface ISitemapResolver {

    /**
     * Resolves the sitemap instructions for a URL "root" (e.g.
     * http://www.example.com).
     * @param httpFetcher the http fetcher executor to use to stream Internet
     *        files if needed
     * @param urlRoot the URL root for which to resolve the sitemap
     * @param sitemapLocations sitemap locations to resolve
     * @param sitemapURLConsumer where to store retrieved site map URLs
     * @param startURLs whether the sitemapLocations provided (if any) are
     *        start URLs (defined in {@link HttpCrawlerConfig#getStartSitemapURLs()})
     */
    void resolveSitemaps(HttpFetchClient httpFetcher, String urlRoot,
            List<String> sitemapLocations,
            Consumer<HttpCrawlReference> sitemapURLConsumer,
            boolean startURLs);

//    /**
//     * Stops any ongoing sitemap resolution.  Some sitemaps can be huge,
//     * and they may take a while to process.  Upon the crawler receiving a
//     * stop request, this method will be invoked and implementors should
//     * try to exit cleanly without much delay.
//     */
//    void stop();
}
