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
package com.norconex.crawler.web.cmd.crawl.operations.sitemap;

@FunctionalInterface
public interface SitemapResolver {

    // this one keeps all the state... the locator just suggests stuff
    // and this one returns right away if already processed for a root
    // url so the suggestions mean nothing.

    //TODO rename back SitemapResolver_OLD when done? Since it does more than
    // parsing (keeping states, plus also fetching).

    void resolve(SitemapContext ctx);
}
