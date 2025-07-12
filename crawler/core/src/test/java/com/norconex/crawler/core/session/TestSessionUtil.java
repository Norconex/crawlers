/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.session;

import java.nio.file.Path;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.grid.core.BaseGridContext;

public final class TestSessionUtil {

    private TestSessionUtil() {
    }

    public static CrawlContext createCrawlerContext(
            CrawlDriver driver, CrawlConfig cfg, Path workDir) {
        var grid = cfg
                .getGridConnector()
                .connect(new BaseGridContext(workDir));
        return CrawlContextFactory.builder()
                .config(cfg)
                .driver(driver)
                .grid(grid)
                .session(new CrawlSession()
                        .setCrawlerId(cfg.getId())
                        .setCrawlMode(CrawlMode.FULL)
                        .setCrawlState(CrawlState.RUNNING)
                        .setLastUpdated(System.currentTimeMillis())
                        .setLaunchMode(LaunchMode.NEW))
                .build()
                .create();
    }

    public static void destroyCrawlerContext(CrawlContext ctx) {
        CrawlContextDestroyer.destroy(ctx);
    }

}
