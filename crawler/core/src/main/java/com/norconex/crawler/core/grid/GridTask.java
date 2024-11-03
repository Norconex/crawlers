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
package com.norconex.crawler.core.grid;

import java.io.Serializable;

import com.norconex.crawler.core.CrawlerContext;

/**
 * Code meant to run on server node (in a clustered environment).
 * @param <T> type of return value, which can be {@link Void}.
 */
@FunctionalInterface
public interface GridTask<T> extends Serializable {
    //void run(CrawlerContext crawlerContext, String arg);
    T run(CrawlerContext crawlerContext, String arg);
}
