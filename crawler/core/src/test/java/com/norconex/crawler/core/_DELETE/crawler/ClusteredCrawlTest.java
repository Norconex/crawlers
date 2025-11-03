/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core._DELETE.crawler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.mocks.crawler.MockCrawlDriverFactory;

/**
 * <p>
 * Executes a crawler one (default) or more times, on a specified number of
 * container nodes (default is 2 nodes).
 * </p>
 * <h3>Log levels</h3>
 * <p>
 * Compatible with {@literal @WithLogLevel} annotation. It will make sure
 * running instances uses the log levels indicated for the specified classes,
 * provided the default SLF4J logger is used (Log4J2).
 * </p>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@TestTemplate
@ExtendWith(ClusteredCrawlTestExtension.class)
public @interface ClusteredCrawlTest {

    public static final String NO_CONFIG = "null";

    public static final class NoCrawlerConfigModifier
            implements Consumer<CrawlConfig> {
        @Override
        public void accept(CrawlConfig t) {
            //NOOP
        }
    }

    /**
     * Text-based crawler configuration. Defaults to {@value NO_CONFIG}.
     * Variable <code>${interpolation}</code> is possible using {@link #vars()}.
     * @return crawler configuration string
     */
    String config() default NO_CONFIG;

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
     * A consumer to modify the configuration obtained from {@link #config()},
     * after variable interpolation was performed.
     * @return crawler configuration consumer
     */
    Class<? extends Consumer<
            ? extends CrawlConfig>> configModifier() default NoCrawlerConfigModifier.class;

    /**
     * The driver supplier class to use for the crawler.
     */
    Class<? extends Supplier<
            CrawlDriver>> driverSupplierClass() default MockCrawlDriverFactory.class;

    /**
     * Number of cluster nodes to create for each test iteration.
     * Can specify multiple values to run the test with different node counts.
     * For example: {@code nodes = {2, 3, 5}} will run the test three times.
     * Defaults to {2}.
     */
    int[] nodes() default { 2 };

    //TODO add attribute to pass config, with placeholders, just like in @CrawlTest
    // and move common logic to shared business class. Same if they share some
    // annotations, share a common construct (e.g., interface).

    /**
     * Additional command-line arguments to pass to the crawler. Defaults
     * to no args.
     */
    String[] cliArgs() default {};

}
