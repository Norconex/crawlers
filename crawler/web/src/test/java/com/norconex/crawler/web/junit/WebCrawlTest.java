/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.web.junit;

import static com.norconex.commons.lang.config.Configurable.configure;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jeasy.random.EasyRandom;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.CrawlTest;
import com.norconex.crawler.core.junit.CrawlTest.Focus;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebCrawlDriverFactory;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.doc.operations.delay.impl.GenericDelayResolver;
import com.norconex.crawler.web.junit.WebCrawlTest.WebConfigRandomizer;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.local.LocalGridConnector;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@CrawlTest(
    driverFactory = WebCrawlDriverFactory.class,
    randomizer = WebConfigRandomizer.class
)
//NOTE: Attributes copied from @CrawlTest
public @interface WebCrawlTest {

    /**
     * Whether to run the crawler, initialize the crawler context, or
     * simply construct the configuration.
     */
    Focus focus() default Focus.CONFIG;

    Class<? extends GridConnector>[] gridConnectors() default {
            LocalGridConnector.class,
            //            IgniteGridTestConnector.class
            //            IgniteGridConnector.class
    };

    boolean randomConfig() default false;

    String config() default "";

    String[] vars() default {};

    Class<? extends Consumer<
            ? extends CrawlConfig>> configModifier() default DefaultWebCrawlerConfigModifier.class;

    public static final class WebConfigRandomizer
            implements Supplier<EasyRandom> {
        @Override
        public EasyRandom get() {
            return WebTestUtil.RANDOMIZER;
        }
    }

    public static final class DefaultWebCrawlerConfigModifier
            implements Consumer<WebCrawlerConfig> {
        @Override
        public void accept(WebCrawlerConfig cfg) {
            cfg.setDelayResolver(configure(new GenericDelayResolver(),
                    dr -> dr.setDefaultDelay(Duration.ofMillis(0))));
        }
    }

}
