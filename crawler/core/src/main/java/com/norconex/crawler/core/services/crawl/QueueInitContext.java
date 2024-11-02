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
package com.norconex.crawler.core.services.crawl;

import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.doc.CrawlDocContext;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * Holds contextual objects necessary to initialize a crawler queue.
 */
@Accessors(fluent = false)
@AllArgsConstructor
public class QueueInitContext {
    @Getter
    private final CrawlerContext crawlerContext;
    @Getter
    private final boolean resuming;
    private final Consumer<CrawlDocContext> queuer;

    public void queue(@NonNull String reference) {
        var rec = crawlerContext.newDocContext(reference);
        rec.setDepth(0);
        queue(rec);
    }

    public void queue(@NonNull CrawlDocContext rec) {
        queuer.accept(rec);
    }
}