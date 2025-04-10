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
package com.norconex.crawler.web.doc.operations.sitemap;

import java.util.List;

import com.norconex.crawler.core.CrawlerContext;

/**
 * <p>
 * Suggestions of sitemap locations associated with a URL web site.
 * Only the first one that exists for a web site will be processed by the
 * {@link SitemapResolver} and it won't be processed again for the same
 * web site within a session. The sitemap locator is not used under
 * the following conditions.
 * </p>
 * <ul>
 *   <li>If the sitemap locator has been disabled (<code>null</code>).</li>
 *   <li>If the sitemap resolver has been disabled (<code>null</code>).</li>
 *   <li>If a sitemap was specified as a start reference for the same
 *       URL web site.</li>
 * </ul>
 */
@FunctionalInterface
public interface SitemapLocator {
    List<String> locations(String url, CrawlerContext crawler);
}
