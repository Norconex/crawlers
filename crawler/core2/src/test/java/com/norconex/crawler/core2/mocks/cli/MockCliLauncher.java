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
package com.norconex.crawler.core2.mocks.cli;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.SystemUtil.Captured;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core2.mocks.crawler.MockCrawlDriverFactory;
import com.norconex.crawler.core2.stubs.CrawlerConfigStubber;
import com.norconex.crawler.core2.util.ConfigUtil;

import lombok.Builder;
import lombok.NonNull;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Builder
public class MockCliLauncher {

    @NonNull
    private final Path workDir;
    @Singular
    private final List<String> args;
    private final Consumer<CrawlConfig> configModifier;
    private final boolean logErrors;

    public MockCliExit launch() {
        var eventFile = workDir.resolve(TimeIdGenerator.next() + ".txt");
        var workDirRef = new MutableObject<Path>();
        Consumer<CrawlConfig> modifierWrapper = cfg -> {
            workDirRef.setValue(ConfigUtil.resolveWorkDir(cfg));
            if (configModifier != null) {
                configModifier.accept(cfg);
            }
            if (cfg.getEventListeners().stream()
                    .noneMatch(MockCliEventWriter.class::isInstance)) {
                var eventWriter = new MockCliEventWriter();
                eventWriter.setEventFile(eventFile);
                cfg.addEventListener(eventWriter);
            }
        };

        var cfgFileStr = getConfigPathStrFromArgs();
        Path cfgFile = null;
        if (StringUtils.isNotBlank(cfgFileStr)) {
            cfgFile = Path.of(cfgFileStr);
            CrawlerConfigStubber.writeOrUpdateConfigToFile(cfgFile,
                    modifierWrapper);

        } else {
            cfgFile = CrawlerConfigStubber.writeConfigToDir(
                    workDir, modifierWrapper);
        }

        // Replace config path with created path if argument was supplied
        // without a file.
        var cmdArgs = args.toArray(ArrayUtils.EMPTY_STRING_ARRAY);
        if ("".equals(cfgFileStr)) {
            for (var i = 0; i < cmdArgs.length; i++) {
                var arg = cmdArgs[i];
                if (StringUtils.equalsAny(arg, "-config=", "-c=")) {
                    cmdArgs[i] = "-config=" + cfgFile;
                }
            }
        }

        var exit = launchVerbatim(cmdArgs);

        if (LOG.isDebugEnabled()) {
            LOG.debug(exit.getStdOut());
        }

        if ((LOG.isDebugEnabled() || logErrors)
                && StringUtils.isNotBlank(exit.getStdErr())) {
            LOG.error(exit.getStdErr());
        }
        exit.getEvents().addAll(MockCliEventWriter.parseEvents(eventFile));
        return exit;
    }

    //    /**
    //     * Does not interpret the command or modify the config file.
    //     * The returned exit object does not have captured events.
    //     * @param cmdArgs arguments
    //     * @return exit value (0 means all good)
    //     */
    //    public static int launchVerbatim(String... cmdArgs) {
    //        return CliCrawlerLauncher.launch(
    //                MockCrawlDriverFactory.create(),
    //                cmdArgs);
    //    }

    /**
     * Does not interpret the command or modify the config file.
     * The returned exit object does not have captured events.
     * @param cmdArgs arguments
     * @return exit object
     */
    public static MockCliExit launchVerbatim(String... cmdArgs) {
        Captured<Integer> captured = SystemUtil.withOutputCapture(
                () -> CliCrawlerLauncher.launch(
                        MockCrawlDriverFactory.create(),
                        cmdArgs));
        var exit = new MockCliExit();
        exit.setCode(captured.getReturnValue());
        exit.setStdOut(captured.getStdOut());
        exit.setStdErr(captured.getStdErr());
        return exit;
    }

    //        /**
    //         * Does not interpret the command or modify the config file.
    //         * The returned exit object does not have captured events.
    //         * @param cmdArgs arguments
    //         * @return exit object
    //         */
    //        public static MockCliExit launchVerbatim(String... cmdArgs) {
    //            MockCliEventWriter.EVENTS.clear();
    //
    //            //TODO callAndCaptureOutput is synchronized... bad for testing
    //            // have a version that is not... or... add the loop within
    //            // the runnable that is passed to it (maybe better).
    //
    //            Captured<Integer> captured = SystemUtil.callAndCaptureOutput(
    //                    () -> CliCrawlerLauncher.launch(
    //                            MockCrawlDriverFactory.create(),
    //                            cmdArgs));
    //            var exit = new MockCliExit();
    //            exit.setCode(captured.getReturnValue());
    //            exit.setStdOut(captured.getStdOut());
    //            exit.setStdErr(captured.getStdErr());
    //            return exit;
    //        }

    // null, no supplied
    // empty, arg name supplied, no value
    // not-empty, has a config file argument
    private String getConfigPathStrFromArgs() {
        if (CollectionUtils.isEmpty(args)) {
            return null;
        }
        for (String arg : args) {
            if (StringUtils.startsWithAny(arg, "-config", "-c")) {
                return StringUtils.substringAfter(arg, "=").trim();
            }
        }
        return null;
    }
}
