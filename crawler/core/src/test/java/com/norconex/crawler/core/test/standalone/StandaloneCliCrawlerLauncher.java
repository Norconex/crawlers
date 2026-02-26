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
package com.norconex.crawler.core.test.standalone;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.SystemUtil.Captured;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.test.CoreTestUtil;
import com.norconex.crawler.core.test.CrawlTestDriver;
import com.norconex.crawler.core.test.EventNameRecorder;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

/**
 * Test launcher for a <em>single</em> crawler running in the same JVM as the
 * test. You have the option to pass your own {@link CrawlConfig} or use
 * the one that will be created for you, provided you supplied a command
 * argument.
 * <p>
 * The launch method will instrument the crawler configuration for
 * testing based on supplied builder properties. That will modifying the
 * instance, if one is passed. You can modify the configuration
 * further by passing a {@link #configModifier}. Doing so can effectively
 * let you overwrite settings already set via the builder properties.
 * </p>
 * <p>
 * Since this launcher emulates command-line invocation, the configuration
 * will be serialized to file and passed as argument with "-config"
 * (don't pass "-config" yourself) as long as you pass at least one other
 * argument.
 * </p>
 * <p>
 * This launcher uses a single {@link MemoryCommitter} by default.
 * Keep your tests low volume when using this default.
 * Mocks are also used for other options.
 * </p>
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

    public StandaloneCliResult launch() {
        return launch(null);
    }

    public StandaloneCliResult launch(CrawlConfig crawlConfig) {
        var driver = ofNullable(crawlDriver).orElseGet(CrawlTestDriver::create);
        var allArgs = new ArrayList<>(args);

        // Only inject a config file if at least one real command is passed
        if (allArgs.stream().anyMatch(arg -> !arg.startsWith("-"))) {
            var config = ofNullable(crawlConfig).orElseGet(
                    () -> ClassUtil.newInstance(driver.crawlerConfigClass()));

            config.setWorkDir(workDir);
            if (StringUtils.isBlank(config.getId())) {
                config.setId(DEFAULT_CRAWLER_ID);
            }

            // Use a shorter idle timeout for tests to avoid 5-second waits
            // at the end of every crawl. The configModifier can override this.
            config.setIdleTimeout(java.time.Duration.ofMillis(500));

            if (configModifier != null) {
                configModifier.accept(config);
            }

            if (config.getCommitters().stream()
                    .noneMatch(MemoryCommitter.class::isInstance)) {
                config.addCommitter(new MemoryCommitter());
            }

            var configFile = CoreTestUtil.writeToDir(config, workDir);
            allArgs.add("-config");
            allArgs.add(configFile.toAbsolutePath().toString());
        }

        // EventNameRecorder is registered on the driver's EventManager so
        // it survives config serialization/deserialization in the CLI path.
        var eventRecorder = new EventNameRecorder();
        driver.eventManager().addListener(eventRecorder);

        LOG.info("CLI arguments: {}", String.join(" ", allArgs));
        var captured = SystemUtil.withOutputCapture(
                () -> CliCrawlerLauncher.launch(
                        driver, allArgs.toArray(new String[0])));

        var exit = new StandaloneCliResult();
        exit.setExitCode(captured.getReturnValue());
        exit.setStdOut(captured.getStdOut());
        exit.setStdErr(captured.getStdErr());
        exit.getEvents().addAll(eventRecorder.getNames());

        if (LOG.isDebugEnabled()) {
            LOG.debug(exit.getStdOut());
        }
        if (printErrors && StringUtils.isNotBlank(exit.getStdErr())) {
            LOG.error(exit.getStdErr());
        }
        return exit;
    }

    /**
     * Launches a crawler as if using {@link CliCrawlerLauncher} directly
     * (no configuration instrumentalization), with
     * {@link CrawlTestDriver} to create the crawler driver,
     * and captures the the return value, STDERR and STDOUT.
     * Unless required, use the {@link #builder()} approach instead.
     * @param args crawler arguments
     * @return captured output
     */
    public static Captured<Integer> capture(String... args) {
        return capture(CrawlTestDriver.create(), args);
    }

    /**
     * Launches a crawler as if using {@link CliCrawlerLauncher} directly
     * (no configuration instrumentalization), with the given
     * {@link CrawlDriver}, and captures the return value, STDERR and STDOUT.
     * Unless required, use the {@link #builder()} approach instead.
     * @param driver crawl driver
     * @param args crawler arguments
     * @return captured output
     */
    public static Captured<Integer> capture(CrawlDriver driver,
            String... args) {
        return SystemUtil.withOutputCapture(
                () -> CliCrawlerLauncher.launch(driver, args));
    }
}
