/* Copyright 2021-2025 Norconex Inc.
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
package com.norconex.crawler.core.metrics;

import java.io.Closeable;

import com.norconex.crawler.core.CrawlerContext;

/**
 * Interface used to access crawler metrics across the application.
 */
public interface CrawlerMetrics extends CrawlerMetricsMXBean, Closeable {

    void init(CrawlerContext crawlerContext);

    /**
     * Flushes the metrics. Implementation specific, but normally forces a
     * commit of any buffered event to storage and/or the
     * synchronization of metrics across nodes.
     */
    void flush();

    @Override
    void close();
}
