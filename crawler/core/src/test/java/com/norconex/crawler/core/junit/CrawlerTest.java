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

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.impl.local.LocalGridConnector;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerSpecProvider;
import com.norconex.crawler.core.mocks.grid.MockIgniteGridConnector;

/**
 * <p>
 * Initializes a crawl session before each test execution and destroys it after
 * each execution.
 * </p>
 */
@Retention(RUNTIME)
@Target({ ElementType.METHOD })
@ExtendWith(CrawlerTestExtension.class)
@TestTemplate
public @interface CrawlerTest {

    /**
     * Use a the given crawler specs.
     * @return crawler specs
     */
    Class<? extends CrawlerSpecProvider> specProvider() default MockCrawlerSpecProvider.class;

    Class<? extends GridConnector>[] gridConnectors() default {
            LocalGridConnector.class, MockIgniteGridConnector.class };

    /**
     * Whether to run the crawler before executing the test. Otherwise
     * it will just initialize it without running it.
     * @return <code>true</code> to run the crawler
     */
    boolean run() default false;

    /**
     * Whether the default crawl session configuration used for testing
     * is filled with random values before any custom configuration
     * is added.
     * @return use random configuration values
     */
    boolean randomConfig() default false;

    /**
     * Text-based crawler configuration, applied on top of generated random
     * configuration if {@link #randomConfig()} is <code>true</code>.
     * @return crawler configuration string
     */
    String config() default "";

    /**
     * A consumer to modify the configuration. Runs after
     * {@link #config()}.
     * @return crawler configuration consumer
     */
    Class<? extends Consumer<
            ? extends CrawlerConfig>> configModifier() default NoCrawlerConfigModifier.class;

    public static final class NoCrawlerConfigModifier
            implements Consumer<CrawlerConfig> {
        @Override
        public void accept(CrawlerConfig t) {
            //NOOP
        }
    }
}
