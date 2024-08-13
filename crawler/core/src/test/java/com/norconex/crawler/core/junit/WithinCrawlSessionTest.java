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
package com.norconex.crawler.core.junit;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.session.CrawlSessionConfig;

/**
 * <p>
 * Initializes a crawl session before each test execution and destroys it after
 * each execution.
 * </p>
 */
@Retention(RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE })
@ExtendWith(CrawlSessionExtension.class)
@Test
public @interface WithinCrawlSessionTest {

    /**
     * Whether the default crawl session configuration used for testing
     * is filled with random values before any custom configuration
     * is added.
     * @return use random configuration values
     */
    boolean randomDefaultConfig() default false;

    /**
     * Text-based configuration for the crawl session. Applied over existing
     * crawl session test configuration. Crawlers can also be
     * configured here but will be overwritten by
     * {@link #crawlerConfigurations()} if set.
     * @return crawl session configuration string
     */
    String crawlSessionConfiguration() default "";

    /**
     * Text-based configuration for all crawlers. Applied over existing
     * crawler test configuration and after
     * {@link #crawlSessionConfiguration()}. Use a single string for
     * a single crawler.
     * @return crawl session configuration string
     */
    String[] crawlerConfigurations() default {};

    /**
     * A consumer free to modify the crawl session configuration
     * used for testing.
     * @return crawl session configuration consumer
     */
    Class<? extends Consumer<? extends CrawlSessionConfig>>
            crawlSessionConfigModifier()
                    default NoCrawlSessionConfigModifier.class;

    /**
     * A consumer free to modify the first crawler from the crawl session
     * configuration used for testing.
     * @return crawler configuration consumer
     */
    Class<? extends Consumer<? extends CrawlerConfig>>
            crawlerConfigModifier() default NoCrawlerConfigModifier.class;


    public static final class NoCrawlSessionConfigModifier
            implements Consumer<CrawlSessionConfig> {
        @Override
        public void accept(CrawlSessionConfig t) {
            //NOOP
        }
    }
    public static final class NoCrawlerConfigModifier
            implements Consumer<CrawlerConfig> {
        @Override
        public void accept(CrawlerConfig t) {
            //NOOP
        }
    }
}

