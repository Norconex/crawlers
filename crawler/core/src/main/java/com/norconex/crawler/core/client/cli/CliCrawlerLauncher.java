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
package com.norconex.crawler.core.client.cli;

import com.norconex.crawler.core.CrawlerBuilderFactory;

import lombok.NonNull;
import picocli.CommandLine;
import picocli.CommandLine.PicocliException;

public final class CliCrawlerLauncher {

    private CliCrawlerLauncher() {
    }

    public static int launch(
            @NonNull Class<
                    ? extends CrawlerBuilderFactory> crawlerBuilderFactoryClass,
            String... args) {

        var cmdLine = new CommandLine(
                new CliCommandRunner(crawlerBuilderFactoryClass));

        cmdLine.setExecutionExceptionHandler(
                (var ex, var cli, var parseResult) -> {
                    if (ex instanceof PicocliException
                            || ex instanceof CliException) {
                        cli.getErr().println(ex.getMessage());
                        cli.getErr().println();
                        cli.usage(cli.getErr());
                        return -1;
                    }
                    throw ex;
                });

        if (args.length == 0) {
            cmdLine.getErr().println("No arguments provided.");
            cmdLine.usage(cmdLine.getOut());
            return -1;
        }
        return cmdLine.execute(args);
    }

}
