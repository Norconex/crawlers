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
package com.norconex.crawler.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.crawler.core.session.CrawlSessionConfig;

/**
 * Mocks an initialized crawl session. By default, it is configured
 * with a single crawler having 1 thread.
 * You add more crawlers and configure them using this annotation options.
 */
@ExtendWith(MockWebCrawlSessionExtension.class)
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface MockWebCrawlSession {

    /**
     * Timeout in milliseconds.
     * Abort the crawl session if it ran longer than supplied timeout value.
     * -1 to disable the timeout. Defaults to 60 seconds.
     * @return timeout in milliseconds
     */
    long timeout() default 60_000; // 1 minute

    /**
     * Number of crawlers to create
     * @return number of crawlers
     */
    int numOfCrawlers() default 1;
    /**
     * Crawl session configuration consumer for modifying settings before
     * a crawl session gets initialized.
     * @return session configuration consumer
     */
    Class<? extends Consumer<CrawlSessionConfig>> configConsumer()
            default NoopConfigConsumer.class;

    class NoopConfigConsumer implements Consumer<CrawlSessionConfig> {
        @Override
        public void accept(CrawlSessionConfig cfg) {
            // NOOP
        }
    }
}
