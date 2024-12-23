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

import com.norconex.crawler.core.CrawlerContext;

/**
 * Long running process or process that runs in parallel to other activities.
 */
public interface GridService {

    /**
     * Initialize the grid service.
     * @param crawlerContext the initialized crawler context
     * @param arg possible service argument
     */
    void init(CrawlerContext crawlerContext, String arg);

    /**
     * Executes the service.  That typically includes starting a process
     * and cleaning after it if necessary when done executing.
     * @param crawlerContext the initialized crawler context
     */
    void execute(CrawlerContext crawlerContext);

    /**
     * Stops the service execution prematurely (if not already stopped).
     * Has to be invoked explicitly. Never called directly by the grid.
     * @param crawlerContext initialized crawler context
     */
    void stop(CrawlerContext crawlerContext);
}
