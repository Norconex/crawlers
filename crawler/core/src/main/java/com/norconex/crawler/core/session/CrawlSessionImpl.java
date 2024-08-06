/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.core.session;

import java.util.function.BiFunction;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.stop.CrawlSessionStopper;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;

/**
 * <p>
 * Inner workings specific to a given crawl session implementation. Not
 * for general use, and not meant to be configured <i>directly</i> at runtime.
 * </p>
 */
@Data
@Builder
@Accessors(fluent = true)
public class CrawlSessionImpl {

    BiFunction<CrawlSession, CrawlerConfig, Crawler> crawlerFactory;

    CrawlSessionConfig crawlSessionConfig;

    BeanMapper beanMapper;

    EventManager eventManager;

    //TODO move this to cluster-session table
    CrawlSessionStopper crawlSessionStopper;

    @NonNull
    @Default
    Class<? extends CrawlerConfig> crawlerConfigClass = CrawlerConfig.class;

}