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
package com.norconex.crawler.core.tasks.crawl;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.GridTask;
import com.norconex.crawler.core.tasks.crawl.process.DocsProcessor;

/**
 * Performs a crawl by getting references from the crawl queue until there
 * are no more in the queue or being processed.
 */
public class CrawlTask implements GridTask {

    private static final long serialVersionUID = 1L;

    @Override
    public void run(CrawlerContext crawlerContext, String arg) {
        //TODO maybe merge DocsProcessor here instead?
        System.err.println("XXX About to run DocsProcessor...");
        new DocsProcessor(crawlerContext).run();
    }

}
