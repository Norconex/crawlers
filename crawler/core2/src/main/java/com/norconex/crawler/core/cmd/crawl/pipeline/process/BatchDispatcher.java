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
package com.norconex.crawler.core.cmd.crawl.pipeline.process;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.ledger.CrawlEntry;

import lombok.Builder;

@Builder
public class BatchDispatcher {
    private final int maxBatchSize;
    private final int lowWatermark;
    private final int pollIntervalMillis;
    private final int maxEmptyPolls;
    private final CrawlSession session;
    private final BlockingQueue<CrawlEntry> localQueue =
            new LinkedBlockingQueue<>();
    private final Object refillLock = new Object();

    public CrawlEntry take() throws InterruptedException {
        var ledger = session.getCrawlContext().getCrawlEntryLedger();
        var emptyPolls = 0;
        while (true) {
            var entry = localQueue.poll();
            if (entry != null) {
                maybeRefill();
                return entry;
            }
            // Only one thread should try to refill when empty
            synchronized (refillLock) {
                if (localQueue.isEmpty()) {
                    var batch = ledger.nextQueuedBatch(computeBatchSize());
                    if (!batch.isEmpty()) {
                        localQueue.addAll(batch);
                        continue;
                    }
                }
            }
            // Wait and retry, up to maxEmptyPolls
            if (++emptyPolls >= maxEmptyPolls) {
                return null; // Give up after N tries
            }
            TimeUnit.MILLISECONDS.sleep(pollIntervalMillis);
        }
    }

    private void maybeRefill() {
        var ledger = session.getCrawlContext().getCrawlEntryLedger();
        if (localQueue.size() <= lowWatermark) {
            synchronized (refillLock) {
                if (localQueue.size() <= lowWatermark) {
                    var batch = ledger.nextQueuedBatch(computeBatchSize());
                    localQueue.addAll(batch);
                }
            }
        }
    }

    public int localQueueSize() {
        return localQueue.size();
    }

    private int computeBatchSize() {
        var ledger = session.getCrawlContext().getCrawlEntryLedger();
        var queueCount = ledger.getQueueCount();
        var nodeCount = session.getCluster().getNodeCount();
        var fairShare = (int) Math.ceil((double) queueCount / nodeCount);
        return Math.max(1, Math.min(maxBatchSize, fairShare));
    }

}