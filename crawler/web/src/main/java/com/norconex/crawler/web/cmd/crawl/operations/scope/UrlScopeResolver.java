/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.web.cmd.crawl.operations.scope;

import com.norconex.crawler.web.doc.WebCrawlDocContext;

public interface UrlScopeResolver {

    //TODO refactor a bit so the cache creation/usage is abstracted from impl
    // having just this here forces impls to know and use the key, or even
    // use the cache altogether.

    public enum SitemapPresence {
        RESOLVING, PRESENT, NONE
    }

    String RESOLVED_SITES_CACHE_NAME =
            UrlScopeResolver.class.getSimpleName() + ".resolvedSites";

    UrlScope resolve(String inScopeURL, WebCrawlDocContext candidateDocContext);
}
