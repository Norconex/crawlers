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
package com.norconex.crawler.core.test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.cmd.crawl.CrawlCommand;
import com.norconex.crawler.core.ledger.ProcessingStatus;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.Getter;
import lombok.Setter;

public class CachesRecorder
        implements BiConsumer<CrawlSession, Class<? extends Command>> {

    @Getter
    private final Map<String, Map<String, String>> caches = new HashMap<>();

    @Setter
    @Getter
    private boolean enabled;

    @Override
    public void accept(CrawlSession session, Class<? extends Command> cmdCls) {

        // We only export caches from the coordinator and if the
        // command is a crawl command
        if (!enabled || !CrawlCommand.class.equals(cmdCls)
                || !session.getCluster().getLocalNode().isCoordinator()) {
            return;
        }

        // Add custom metrics to session cache
        var sessionAttr = session.getSessionAttributes();
        var ledger = session.getCrawlContext().getCrawlEntryLedger();
        Arrays.stream(ProcessingStatus.values()).forEach(st -> {
            sessionAttr.setInteger("status-counter-" + st.name(),
                    (int) ledger.countByStatus(st));
        });

        var cacheManager = session.getCluster().getCacheManager();
        cacheManager.exportCaches(serialCache -> {
            var cacheMap = caches.computeIfAbsent(
                    serialCache.getCacheName(), cn -> new HashMap<>());
            for (var entry : serialCache) {
                cacheMap.put(entry.getKey(), entry.getJson());
            }
        });
    }
}
