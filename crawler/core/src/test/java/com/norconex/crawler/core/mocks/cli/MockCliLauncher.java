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
package com.norconex.crawler.core.mocks.cli;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.mutable.MutableObject;

import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.SystemUtil.Captured;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.cli.CliCrawlerLauncher;
import com.norconex.crawler.core.mocks.crawler.MockCrawlerSpecProvider;
import com.norconex.crawler.core.stubs.StubCrawlerConfig;

import lombok.NonNull;

public class MockCliLauncher {
    public static MockCliExit launch(
            @NonNull Path workDir,
            String... cmdArgs) {
        return launch(workDir, null, cmdArgs);
    }

    public static MockCliExit launch(
            @NonNull Path workDir,
            Consumer<CrawlerConfig> configModifier,
            String... cmdArgs) {

        //NOTE configuration will be read from file, but applied on top
        // of existing config so we can pre-configure items here.
        var cfgFile = StubCrawlerConfig.writeConfigToDir(workDir, cfg -> {
            if (configModifier != null) {
                configModifier.accept(cfg);
            }
            cfg.addEventListener(new MockCliEventWriter());
        });

        // replace config path with created path if argument was supplied
        // without a file
        for (var i = 0; i < cmdArgs.length; i++) {
            var arg = cmdArgs[i];
            if (StringUtils.equalsAny(arg, "-config=", "-c=")) {
                cmdArgs[i] = "-config=" + cfgFile;
            }
        }

        new MutableObject<CrawlerConfig>();

        Captured<Integer> captured = SystemUtil.callAndCaptureOutput(
                () -> CliCrawlerLauncher.launch(
                        MockCrawlerSpecProvider.class,
                        cmdArgs));

        var exit = new MockCliExit();
        exit.setCode(captured.getReturnValue());
        exit.setStdOut(captured.getStdOut());
        exit.setStdErr(captured.getStdErr());
        try {
            var evtFile = workDir.resolve(MockCliEventWriter.EVENTS_FILE_NAME);
            exit.getEvents().addAll(Files.readAllLines(evtFile));
            Files.delete(evtFile);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return exit;
    }
}
