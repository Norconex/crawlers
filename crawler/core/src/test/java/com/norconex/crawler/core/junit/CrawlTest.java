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
import java.util.function.Supplier;

import org.jeasy.random.EasyRandom;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.impl.ignite.LocalIgniteGridConnector;
import com.norconex.crawler.core.grid.impl.local.LocalGridConnector;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerSpecProvider;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

/**
 * <p>
 * Tests methods annotated with this annotation will have crawler-related
 * objects configured and set to a desired state before
 * making them available as method arguments.
 * </p>
 * <p>
 * Based on the value of annotation {@link #focus()}, either a crawler
 * instance will be run, the context initialized, or simply the configuration
 * and context created without initialization. Refer to {@link CrawlTest.Focus}
 * values for details. Default focus is {@link Focus#CONFIG}.
 * </p>
 */
@Retention(RUNTIME)
@Target({ ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE })
@ExtendWith(CrawlTestExtension.class)
@TestTemplate
public @interface CrawlTest {

    // Execution order:
    //    1. TestTemplateInvocationContext#getAdditionalExtensions()
    //    2. BeforeTestExecutionCallback#beforeTestExecution()
    //    3. ParameterResolver#resolveParameter()
    //    4. Test template method execution.

    /**
     * <p>
     * Focus of the test dictating what will happen before each test execution.
     * The following types are resolved when passed as method arguments:
     * </p>
     * <ul>
     *   <li>
     *     Crawler (when focus is {@link Focus#CRAWL}, else <code>null</code>)
     *   </li>
     *   <li>
     *     CrawlerConfig
     *   </li>
     *   <li>
     *     CrawlerContext (<code>null</code> when focus is {@link Focus#CONFIG})
     *   </li>
     *   <li>
     *     Committer (an instance of <code>MemoryCommitter</code>)
     *   </li>
     *   <li>
     *     Path (a temporary working directory)
     *   </li>
     * </ul>
     */
    public static enum Focus {
        /**
         * Before each text execution, create a crawler, invoke the crawl
         * command, and destroy the crawler after each execution.
         * All resolvable method arguments are set (non-<code>null</code>).
         */
        CRAWL,
        /**
         * Initializes the crawler context before each text execution and
         * closes it after each.
         * Does not crawl and outcome is unexpected if trying to launch a crawl
         * while the separate crawler context is active.
         * The resolvable <code>Crawler</code> method arguments is always
         * <code>null</code>.
         */
        CONTEXT,
        /**
         * Makes resolvable method arguments available without initialization.
         * The resolvable <code>Crawler</code> method arguments is always
         * <code>null</code>.
         */
        CONFIG
    }

    /**
     * Use a the given crawler specs.
     * @return crawler specs
     */
    Class<? extends CrawlerSpecProvider> specProvider() default MockCrawlerSpecProvider.class;

    Class<? extends GridConnector>[] gridConnectors() default {
            LocalGridConnector.class,
            LocalIgniteGridConnector.class
    };

    /**
     * Whether to run the crawler, initialize the crawler context, or
     * simply construct the configuration.
     */
    Focus focus() default Focus.CONFIG;

    /**
     * Whether the default crawl session configuration used for testing
     * is filled with random values before any custom configuration
     * is added.
     * @return use random configuration values
     */
    boolean randomConfig() default false;

    /**
     * Whether the default crawl session configuration used for testing
     * is filled with random values before any custom configuration
     * is added.
     * @return use random configuration values
     */
    Class<? extends Supplier<
            EasyRandom>> randomizer() default DefaultRandomizer.class;

    /**
     * Text-based crawler configuration, applied on top of generated random
     * configuration if {@link #randomConfig()} is <code>true</code>.
     * Variable <code>${interpolation}</code> is possible using {@link #vars()}.
     * @return crawler configuration string
     */
    String config() default "";

    /**
     * <p>
     * Pairs of key-values to be used as variable for the configuration
     * provided by {@link #config()}. Each array entry is an alternating
     * key or value. Example:
     * </p>
     * <pre>
     * {@literal @}CrawlTest(
     *   config = """
     *       startReferences:
     *         - "${ref1}"
     *         - "${ref2}"
     *       """,
     *   vars = {
     *      "ref1", "http://example.com/page1.html",
     *      "ref2", "http://example.com/page2.html"
     *   }
     * )
     * </pre>
     * @return variables string
     */
    String[] vars() default {};

    /**
     * A consumer to modify the configuration obtained from {@link #config()}.
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

    public static final class DefaultRandomizer
            implements Supplier<EasyRandom> {
        @Override
        public EasyRandom get() {
            return StubCrawlerConfig.RANDOMIZER;
        }
    }
}
