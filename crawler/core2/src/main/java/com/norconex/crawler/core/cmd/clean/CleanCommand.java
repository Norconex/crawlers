/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.cmd.clean;

import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.event.CrawlerEvent;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanCommand implements Command {

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();
        Thread.currentThread().setName(ctx.getId() + "/CLEAN");

        session.getCrawlContext().getCommitterService().clean();

        // Cleaning the cache should be done by a single node.
        if (session.getCluster().getLocalNode().isCoordinator()) {
            session.fire(CrawlerEvent.CRAWLER_CLEAN_BEGIN, this);
            session.getCluster().getCacheManager()
                    .forEach((cacheName, cache) -> {
                        cache.clear();
                    });
            LOG.info("Clean command executed.");
            session.fire(CrawlerEvent.CRAWLER_CLEAN_END, this);
        } else {
            LOG.warn("""
                    Cleaning of caches can only be performed on a single node. \
                    Another node started that cleaning process so this one \
                    will ignore the cache cleaning request.""");
        }

        //        //TODO shall we destroy (i.e., delete physical DB) instead of clear?
        //
        //        //        session.getCluster()
        //        //                .getTaskManager().runOnOneOnceSync(
        //        //                        "cleanTask", sess -> {
        //        //                            sess.getCrawlContext().getCommitterService()
        //        //                                    .clean();
        //        //                            // Close metrics prematurely, before cleaning, or
        //        //                            // it will want to report on a blown-away store:
        //        //                            sess.getCrawlContext().getMetrics().close();
        //        //                            return null;
        //        //                            //                            cntx.getCluster().getCacheManager().destroy();
        //        //                        });
        //        //                .getCompute()
        //        //                .executeTask(GridTaskBuilder.create("cleanTask")
        //        //                        .singleNode()
        //        //                        .processor(grid -> {
        //        //                            var cntx = CrawlContext.get(grid);
        //        //                            cntx.getCommitterService().clean();
        //        //                            // Close metrics prematurely, before cleaning, or
        //        //                            // it will want to report on a blown-away store:
        //        //                            cntx.getMetrics().close();
        //        //                            cntx.getGrid().getStorage().destroy();
        //        //                        })
        //        //                        .build());
        //        //        if (result.getState() != TaskState.COMPLETED) {
        //        //            LOG.warn("Command returned with a non-completed status: {}",
        //        //                    result);
        //        //        } else {
        //        LOG.info("Clean command executed.");
        //        //        }
        //        session.fire(CrawlerEvent.CRAWLER_CLEAN_END, this);
    }
}
