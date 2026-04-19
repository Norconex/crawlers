/* Copyright 2024-2026 Norconex Inc.
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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.hazelcast.config.MapStoreConfig.InitialLoadMode;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastClusterConnector;
import com.norconex.crawler.core.cluster.impl.hazelcast.JdbcHazelcastConfigurer;
import com.norconex.crawler.web.WebCrawlConfig;
import com.norconex.crawler.web.doc.operations.delay.impl.GenericDelayResolver;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@Test
@ExtendWith(WebCrawlExtension.class)
public @interface WebCrawlTest {

    boolean randomConfig() default false;

    String config() default "";

    String[] vars() default {};

    Class<? extends Consumer<
            ? extends CrawlConfig>> configModifier() default DefaultWebCrawlerConfigModifier.class;

    public static final class DefaultWebCrawlerConfigModifier
            implements Consumer<WebCrawlConfig> {
        @Override
        public void accept(WebCrawlConfig cfg) {
            cfg.setDelayResolver(configure(
                    new GenericDelayResolver(),
                    dr -> dr.setDefaultDelay(
                            Duration.ofMillis(0))));

            var connector = cfg.getClusterConfig().getConnector();
            if (connector instanceof HazelcastClusterConnector hzConnector
                    && hzConnector.getConfiguration()
                            .getConfigurer() instanceof JdbcHazelcastConfigurer jdbcConfigurer) {
                jdbcConfigurer
                        .setJetEnabled(false)
                        .setBackupCount(0)
                        .setInitialLoadMode(
                                InitialLoadMode.EAGER);
            }
        }
    }

}
