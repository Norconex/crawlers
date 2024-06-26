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
package com.norconex.crawler.web.crawler;

import java.util.Collections;
import java.util.Map;

import org.apache.commons.collections4.map.LRUMap;

import com.norconex.crawler.core.crawler.CrawlerImplContext;

import lombok.Data;

/**
 * Crawler implementation-specific contextual data. Useful for keeping state
 * between components that exist for specific crawler implementations only.
 */
@Data
public class WebCrawlerContext extends CrawlerImplContext {

    public enum SitemapPresence { RESOLVING, PRESENT, NONE }

    // key = root url
    private final Map<String, SitemapPresence> resolvedWebsites =
            Collections.synchronizedMap(new LRUMap<>(10_000));

}
