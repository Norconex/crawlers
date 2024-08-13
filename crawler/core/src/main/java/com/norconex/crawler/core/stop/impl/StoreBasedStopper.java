/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.stop.impl;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.stop.CrawlSessionStopper;
import com.norconex.crawler.core.stop.CrawlSessionStopperException;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Uses store engine to notify we need to stop.
 */
@EqualsAndHashCode
@ToString
@Slf4j
public class StoreBasedStopper implements CrawlSessionStopper {

//    private boolean monitoring;

    //TODO needed if state runners are handling it?
    @Override
    public void listenForStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException {
        // NOOP
//        var scheduler = Executors.newScheduledThreadPool(1);
//        monitoring = true;
//        scheduler.scheduleAtFixedRate(() -> {
//            MdcUtil.setCrawlSessionId(crawlSession.getId());
//            Thread.currentThread().setName(
//                    crawlSession.getId() + "-stop-store-monitor");
//            if (monitoring ) {
//                LOG.info("STOP request received.");
//                monitoring = false;
//                crawlSession.stopInstance();
//                scheduler.shutdownNow();
//            } else if (!monitoring && !scheduler.isShutdown()) {
//                scheduler.shutdownNow();
//            }
//            //TODO no point going faster than cluster syncs
//            // but use configured interval (once we support configuration)
//        }, 1000, 5 * 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws CrawlSessionStopperException {
      //monitoring = false;
        //NOOP
    }

    @Override
    public boolean fireStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException {
        // Store-based stopping does not need much logic.
        // We just need to inform the store we are stopping and it will
        // be picked up by the crawl session service, which is already
        // listening for any state change and will react to it.
        // That's why we can just call stop directly and not have our own
        // listener here.
        crawlSession.getService().stop();
        LOG.info("Stop request issued.");
        return true;
    }
}
