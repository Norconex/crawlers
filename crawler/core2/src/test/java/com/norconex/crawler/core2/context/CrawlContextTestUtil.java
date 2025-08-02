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
package com.norconex.crawler.core2.context;

import com.norconex.crawler.core2.CrawlConfig;
import com.norconex.crawler.core2.CrawlDriver;
import com.norconex.crawler.core2.session.CrawlMode;
import com.norconex.crawler.core2.session.CrawlSessionSnapshot;
import com.norconex.crawler.core2.session.CrawlState;
import com.norconex.crawler.core2.session.LaunchMode;

public final class CrawlContextTestUtil {

    private CrawlContextTestUtil() {
    }

    public static CrawlContext createCrawlerContext(
            CrawlDriver driver, CrawlConfig cfg) {
        //, Path workDir) {
        //        var grid = cfg
        //                .getGridConnector()
        //                .connect(new BaseGridConnectionContext(workDir,
        //                        cfg.getId()));
        var cluster = cfg.getCluster();
        return CrawlContextFactory.builder()
                .config(cfg)
                .driver(driver)
                .cluster(cluster)
                .session(new CrawlSessionSnapshot()
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
