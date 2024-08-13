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

/**
 * Uses either the file-based stopper or the store-based stopper
 * depending whether the configured store in each crawler
 * is embedded or not.
 */
@EqualsAndHashCode
@ToString
public class ContextBasedStopper implements CrawlSessionStopper {

    private CrawlSessionStopper sessionStopper;

    @Override
    public void listenForStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException {
        sessionStopper = crawlSession.getDataStoreEngine().clusterFriendly()
                ? new StoreBasedStopper() : new FileBasedStopper();
        sessionStopper.listenForStopRequest(crawlSession);
    }

    @Override
    public void destroy() throws CrawlSessionStopperException {
        if (sessionStopper != null) {
            sessionStopper.destroy();
        }
    }

    @Override
    public boolean fireStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException {
        return sessionStopper != null
                && sessionStopper.fireStopRequest(crawlSession);
    }
}
