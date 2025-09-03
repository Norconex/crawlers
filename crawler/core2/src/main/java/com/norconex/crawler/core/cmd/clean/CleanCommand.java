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
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CleanCommand implements Command {

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();
        Thread.currentThread().setName(ctx.getId() + "/CLEAN");
        session.fire(CrawlerEvent.CRAWLER_CLEAN_BEGIN, this);

        session.getCrawlContext().getCommitterService().clean();

        // Cleaning the cache should be done by a single node.
        // Use a lightweight cluster-wide lock in the admin cache to ensure
        // only one node performs the clear even under racy coordinator checks.
        var isCoordinator = session.getCluster().getLocalNode().isCoordinator();
        if (isCoordinator) {
            var cacheManager = session.getCluster().getCacheManager();
            var ephemeralCache = cacheManager.getCrawlRunCache();
            var lockKey = "clean.lock";
            var ownerId = ctx.getId() + ":" + Thread.currentThread().getName();
            var acquired = ephemeralCache.putIfAbsent(lockKey, ownerId) == null;
            if (acquired) {
                try {
                    cacheManager.forEach((cacheName, cache) -> {
                        cache.clear();
                    });
                    LOG.info("Clean command executed.");
                } finally {
                    // Only remove if still owned by us
                    // (avoid clearing someone else's lock in rare races)
                    var current = ephemeralCache.get(lockKey);
                    if (ownerId.equals(current.orElse(null))) {
                        ephemeralCache.remove(lockKey);
                    }
                }
            } else {
                LOG.warn("Another node is already performing cache cleaning "
                        + "(lock held); skipping cache clear on this node.");
            }
        } else {
            LOG.warn("""
                    Cleaning of caches can only be performed on a single node. \
                    Another node started that cleaning process so this one \
                    will ignore the cache cleaning request.""");
        }
        session.fire(CrawlerEvent.CRAWLER_CLEAN_END, this);
    }
}
