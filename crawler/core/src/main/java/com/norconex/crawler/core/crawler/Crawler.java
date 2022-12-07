/* Copyright 2014-2022 Norconex Inc.
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
package com.norconex.crawler.core.crawler;

import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>Abstract crawler implementation providing a common base to building
 * crawlers.</p>
 *
 * <p>As of 1.6.1, JMX support is disabled by default.  To enable it,
 * set the system property "enableJMX" to <code>true</code>.  You can do so
 * by adding this to your Java launch command:
 * </p>
 * <pre>
 *     -DenableJMX=true
 * </pre>
 *
 *
 * @see CrawlerConfig
 */
@Slf4j
@SuperBuilder
@RequiredArgsConstructor
public class Crawler {

    private final CrawlerConfig crawlerConfig;


//    private CrawlSession crawlSession;


    public String getId() {
        return crawlerConfig.getId();
    }

    public void stop() {
//        getEventManager().fire(
//                new CrawlerEvent.Builder(CRAWLER_STOP_BEGIN, this).build());
//        stopped = true;
//        LOG.info("Stopping the crawler.");
    }



//    public static class CrawlerBuilder {
//
//
//        public Crawler create(CrawlerConfig crawlerConfig) {
//
//        }
//    }
}
