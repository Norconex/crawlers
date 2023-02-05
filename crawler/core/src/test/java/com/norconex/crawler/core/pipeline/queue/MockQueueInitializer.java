/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.core.pipeline.queue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.crawler.CrawlerImpl.QueueInitContext;
import com.norconex.crawler.core.doc.CrawlDocRecord;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class MockQueueInitializer
        implements Function<QueueInitContext, MutableBoolean> {

    private final List<String> startReferences = new ArrayList<>();
    private boolean async;
    private long delay;
    private final MutableBoolean done = new MutableBoolean(false);

    public MockQueueInitializer(String... startReferences) {
        this.startReferences.addAll(List.of(startReferences));
    }

    @Override
    public MutableBoolean apply(QueueInitContext ctx) {
        if (async) {
            new Thread(() -> queueAll(ctx)).start();
        } else {
            queueAll(ctx);
        }
        return done;
    }

    private void queueAll(QueueInitContext ctx) {
        for (String ref : startReferences) {
            if (ctx.getCrawler().isStopped()) {
                LOG.info("Crawler stop requested, no longer queuing.");
                break;
            }
            Sleeper.sleepMillis(getDelay());
            ctx.queue(new CrawlDocRecord(ref));
        }
        done.setTrue();
    }
}
