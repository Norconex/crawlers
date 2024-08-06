/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.core.cli;

import com.norconex.crawler.core.session.CrawlSessionImpl;

import lombok.NonNull;
import picocli.CommandLine;

/**
 * Helper for launching a crawl session from a string array representing
 * command line arguments.
 */
public final class CliLauncher {

    private CliLauncher() {}

    public static int launch(
            @NonNull CrawlSessionImpl crawlSessionImpl, String... args) {
        var cmd = new MainCommand(crawlSessionImpl);
        var cmdLine = new CommandLine(cmd);

        cmdLine.setExecutionExceptionHandler(cmd);

        if (args.length == 0) {
            cmdLine.getErr().println("No arguments provided.");
            cmdLine.usage(cmdLine.getOut());
            return -1;
        }

        return cmdLine.execute(args);
    }
}
