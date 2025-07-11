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

import static com.norconex.crawler.core.util.ExceptionSwallower.swallow;

import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.util.ConfigUtil;
import com.norconex.grid.core.BaseGridContext;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.compute.GridTaskBuilder;
import com.norconex.grid.core.storage.GridMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public final class CrawlSessionManager {

    private static final String SESSION_STORE_KEY = "crawlSession";

    //TODO make configurable?
    private static final Duration SESSION_HEARTBEAT_INTERVAL =
            Duration.ofMinutes(2);
    private static final Duration SESSION_TIMEOUT = Duration.ofMinutes(4);

    private static final String GRID_WORKDIR_NAME = "grid";

    private final CrawlDriver crawlDriver;
    private final CrawlConfig crawlConfig;

    public void withCrawlContext(Consumer<CrawlContext> consumer) {
        var heartbeatScheduler = Executors.newScheduledThreadPool(1);
        CrawlContext ctx = null;
        try {
            var grid = crawlConfig
                    .getGridConnector()
                    .connect(new BaseGridContext(
                            ConfigUtil.resolveWorkDir(crawlConfig)
                                    .resolve(GRID_WORKDIR_NAME)));
            var session = CrawlSessionResolver.resolve(
                    grid, SESSION_TIMEOUT, crawlConfig.getId());
            ctx = CrawlContextFactory.builder()
                    .config(crawlConfig)
                    .driver(crawlDriver)
                    .grid(grid)
                    .session(session)
                    .build()
                    .create();

            // Schedule heartbeats at interval
            final var finalCtx = ctx;
            heartbeatScheduler.scheduleAtFixedRate(
                    () -> updateHeartbeat(finalCtx),
                    0,
                    SESSION_HEARTBEAT_INTERVAL.toMillis(),
                    TimeUnit.MILLISECONDS);

            consumer.accept(ctx);
        } finally {
            // Ensure cleanup happens even if an exception occurs
            swallow(heartbeatScheduler::shutdown);
            CrawlContextDestroyer.destroy(ctx);
        }
    }

    static void updateHeartbeat(CrawlContext ctx) {
        // Used pretty much only for joining node to figure out the job
        // status (if one exists, is running, etc.).
        var id = ctx.getId();
        ctx.getGrid().getCompute().executeTask(GridTaskBuilder
                .create("crawlerRunningHeartbeat")
                .singleNode()
                .processor(grid -> sessionStore(grid)
                        .update(id, sess -> sess.setLastUpdated(
                                System.currentTimeMillis())))
                .build());
    }

    static GridMap<CrawlSession> sessionStore(Grid grid) {
        return grid.getStorage().getMap(SESSION_STORE_KEY, CrawlSession.class);
    }
}
