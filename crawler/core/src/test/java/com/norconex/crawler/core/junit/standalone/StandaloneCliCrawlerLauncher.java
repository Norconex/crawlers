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
package com.norconex.crawler.core.junit.standalone;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.SystemUtil.Captured;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.event.listeners.TestEventMemory;
import com.norconex.crawler.core.event.listeners.TestEventMemoryListener;
import com.norconex.crawler.core.junit.CrawlerExecutionResult;
import com.norconex.crawler.core.mocks.crawler.TestCrawlDriverFactory;
import com.norconex.crawler.core.util.CoreTestUtil;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

/**
 * Test launcher for a *single* crawler running in the same JVM as the test.
 * You have the option to pass your own {@link CrawlConfig} or use
 * the one that will be created for you, provided you supplied a command
 * argument.
 *
 * The launch method will instrument the crawler configuration for
 * testing based on supplied builder properties. That will modifying the
 * instance, if one is passed.  You can modify the configuration
 * further by passing a {@link #configModifier}. Doing so can effectively
 * let you overwrite settings already set via the builder properties.
 *
 * Since this launcher emulates command-line invocation, the configuration
 * will be serialized to file and passed as argument with "-config"
 * (don't pass "-config" yourself) as long as you pass at least one other
 * argument.
 *
 * This launcher uses a single {@link MemoryCommitter} by default.
 * Keep your tests low volume when using this default.
 * Mocks are also used for other options.
 */
@Slf4j
@Builder
public class StandaloneCliCrawlerLauncher {

    public static final String DEFAULT_CRAWLER_ID = "test-crawler";

    @NonNull
    private final Path workDir;
    @Singular
    private final List<String> args;
    /**
     * Configuration modifier invoked after this test launcher has instrumented
     * the configuration.
     */
    private final Consumer<CrawlConfig> configModifier;
    private final boolean printErrors;
    private final CrawlDriver crawlDriver;

    public StandaloneExecutionResult launch() {
        return launch(null);
    }

    public StandaloneExecutionResult launch(CrawlConfig crawlConfig) {
        var driver = ofNullable(crawlDriver).orElseGet(
                TestCrawlDriverFactory::create);
        var allArgs = new ArrayList<>(args);

        // only consider config if at least one command argument is passed
        if (allArgs.stream().anyMatch(arg -> !arg.startsWith("-"))) {
            var config = ofNullable(crawlConfig).orElseGet(
                    () -> ClassUtil.newInstance(driver.crawlerConfigClass()));

            config.setWorkDir(workDir);
            if (StringUtils.isBlank(config.getId())) {
                config.setId(DEFAULT_CRAWLER_ID);
            }

            if (configModifier != null) {
                configModifier.accept(config);
            }

            if (config.getEventListeners().stream()
                    .noneMatch(TestEventMemoryListener.class::isInstance)) {
                config.addEventListener(new TestEventMemoryListener());
            }

            if (config.getCommitters().stream()
                    .noneMatch(MemoryCommitter.class::isInstance)) {
                config.addCommitter(new MemoryCommitter());
            }

            var configFile = CoreTestUtil.writeConfigToDir(config, workDir);
            allArgs.add("-config");
            allArgs.add(configFile.toAbsolutePath().toString());
        }

        // Launch
        var exit = new StandaloneExecutionResult();
        LOG.info("CLI arguments: {}", String.join(" ", allArgs));
        try (var memEvents = TestEventMemory.create()) {
            Captured<Integer> captured =
                    CoreTestUtil.withIpv4(() -> SystemUtil.withOutputCapture(
                            () -> CliCrawlerLauncher.launch(
                                    driver, allArgs.toArray(
                                            ArrayUtils.EMPTY_STRING_ARRAY))));
            exit.setExitCode(captured.getReturnValue());
            exit.setStdOut(captured.getStdOut());
            exit.setStdErr(captured.getStdErr());
            exit.getEvents().addAll(TestEventMemory.getEvents());
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(exit.getStdOut());
        }
        if ((LOG.isDebugEnabled() || printErrors)
                && StringUtils.isNotBlank(exit.getStdErr())) {
            LOG.error(exit.getStdErr());
        }
        return exit;
    }

    /**
     * Launches the crawler and returns the result as a unified
     * {@link CrawlerExecutionResult} interface (instead of
     * {@link StandaloneExecutionResult}).
     * This allows using the same result type for both CLI and cluster tests.
     * @return unified crawler execution result
     */
    public CrawlerExecutionResult launchAndGetResult() {
        return launchAndGetResult(null);
    }

    /**
     * Launches the crawler with the given config and returns the result
     * as a unified {@link CrawlerExecutionResult} interface.
     * @param crawlConfig the crawler configuration
     * @return unified crawler execution result
     */
    public CrawlerExecutionResult launchAndGetResult(CrawlConfig crawlConfig) {
        return launch(crawlConfig);
    }

    /**
     * Launches a crawler as if using {@link CliCrawlerLauncher} directly
     * (no configuration instrumentalization), with
     * {@link TestCrawlDriverFactory} to create the crawler driver,
     * and captures the the return value, STDERR and STDOUT.
     * Unless required, use the {@link #builder()} approach instead.
     * @param args crawler arguments
     * @return captured output
     */
    public static Captured<Integer> capture(String... args) {
        return capture(TestCrawlDriverFactory.create(), args);
    }

    /**
     * Launches a crawler as if using {@link CliCrawlerLauncher} directly
     * (no configuration instrumentalization) and captures the the return
     * value, STDERR and STDOUT.
     * Unless required, use the {@link #builder()} approach instead.
     * @param driver crawler driver
     * @param args crawler arguments
     * @return captured output
     */
    public static Captured<Integer> capture(
            CrawlDriver driver, String... args) {
        return SystemUtil.withOutputCapture(
                () -> CliCrawlerLauncher.launch(driver, args));
    }
}
