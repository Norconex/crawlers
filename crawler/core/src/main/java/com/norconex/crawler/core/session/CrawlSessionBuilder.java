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
package com.norconex.crawler.core.session;

import java.util.function.BiFunction;
import java.util.function.Consumer;

import com.norconex.commons.lang.bean.BeanMapper.BeanMapperBuilder;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.stop.CrawlSessionStopper;

import lombok.AccessLevel;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.experimental.Accessors;

@Data
@Accessors(fluent = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CrawlSessionBuilder {

    BiFunction<CrawlSession, CrawlerConfig, Crawler> crawlerFactory;
    CrawlSessionConfig crawlSessionConfig;
    EventManager eventManager;
    //TODO consider making this part of session config?
    CrawlSessionStopper crawlSessionStopper;
    @NonNull
    Class<?> crawlerConfigClass = CrawlerConfig.class;
    Consumer<BeanMapperBuilder> beanMapperCustomizer;

    public CrawlSession build() {
        return new CrawlSession(this);
    }
}