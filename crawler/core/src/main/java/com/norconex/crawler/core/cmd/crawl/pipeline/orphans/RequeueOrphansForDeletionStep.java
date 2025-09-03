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
package com.norconex.crawler.core.cmd.crawl.pipeline.orphans;

import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.crawler.core.cluster.pipeline.BaseStep;
import com.norconex.crawler.core.session.CrawlSession;

import lombok.extern.slf4j.Slf4j;

/**
 * Queue orphans for deletion.
 */
@Slf4j
public class RequeueOrphansForDeletionStep extends BaseStep {

    public RequeueOrphansForDeletionStep(String id) {
        super(id);
    }

    // returns the type of processing we do on orphans, or null if there
    // is nothing to do
    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();

        var orphanCount = ctx.getCrawlEntryLedger().getPreviousEntryCount();
        if (orphanCount == 0) {
            LOG.info("There are no orphans to process.");
            return;
        }

        LOG.info("Queueing orphan references for deletion...");

        var count = new MutableLong();
        ctx.getCrawlEntryLedger().forEachPrevious(entry -> {
            entry.setDeleted(true);
            ctx.getCrawlEntryLedger().queue(entry);
            count.increment();
        });
        LOG.info("{} orphan references queued for deletion.", count);
    }
}
