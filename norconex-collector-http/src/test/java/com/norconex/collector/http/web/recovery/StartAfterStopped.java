/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.recovery;

import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.HttpCollectorEvent;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerEvent;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.Sleeper;

/**
 * Test that the right amount of docs are crawled after stoping
 * and starting the collector.
 * @author Pascal Essiembre
 */
public class StartAfterStopped extends AbstractInfiniteDepthTestFeature {

    protected boolean isResuming() {
        return false;
    }

    @Override
    public int numberOfRun() {
        return 2;
    }

    @Override
    protected void doConfigureCollector(HttpCollectorConfig cfg)
            throws Exception {

        // Make it stop after 3 documents were processed
        if (isFirstRun()) {
            final MutableObject<HttpCollector> col = new MutableObject<>();
            final MutableInt addCount = new MutableInt();
            cfg.addEventListeners(e -> {
                if (e.getSource() instanceof HttpCollector
                        && e.is(HttpCollectorEvent.COLLECTOR_RUN_BEGIN)) {
                    col.setValue((HttpCollector) e.getSource());
                } else if (e.is(HttpCrawlerEvent.DOCUMENT_COMMITTED_ADD)) {
                    if (addCount.incrementAndGet() == 3) {
                        col.getValue().stop();
                        // wait 2 seconds so there is enough time for the
                        // stop file monitor to detect the stop request.
                        Sleeper.sleepSeconds(2);
                    }
                }
            });
        }
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg)
            throws Exception {
        cfg.setStartURLs(cfg.getStartURLs().get(0) + "?depth=0");
        cfg.setMaxDepth(10);
        if (isSecondRun() && !isResuming()) {
            cfg.setMaxDocuments(2);
        } else {
            cfg.setMaxDocuments(10);
        }
    }

    @Override
    public void startCollector(HttpCollector collector) throws Exception {
        if (!isResuming()) {
            collector.clean();
        }
        collector.start();
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {

        if (isFirstRun()) {
            assertListSize("document", committer.getUpsertRequests(), 3);
        } else if (isResuming()) {
            // since 3 were crawled on first run, now should be remaining 7
            assertListSize("document", committer.getUpsertRequests(), 7);
        } else {
            assertListSize("document", committer.getUpsertRequests(), 2);
        }
    }
}
