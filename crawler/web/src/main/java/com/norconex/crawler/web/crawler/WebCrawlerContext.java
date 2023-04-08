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
package com.norconex.crawler.web.crawler;

import com.norconex.crawler.core.crawler.CrawlerImplContext;

import lombok.Data;

/**
 * Crawler implementation-specific contextual data. Useful for keeping state
 * between components that existing for a crawler implementation only.
 */
@Data
public class WebCrawlerContext extends CrawlerImplContext {

    // Maybe have a @Builder or otherwise ensure properties defined here
    // cannot be modified. But take into account this context is not
    // initialized on its own... it is for using classes to initialize
    // whenever if need be.
//    private DataStore<SitemapRecord> sitemapStore;

}
