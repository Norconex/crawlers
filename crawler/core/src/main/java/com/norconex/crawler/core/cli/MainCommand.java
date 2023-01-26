/* Copyright 2019-2022 Norconex Inc.
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

import java.util.concurrent.Callable;

import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSession.CrawlSessionBuilder;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PicocliException;
import picocli.CommandLine.Spec;

/**
 * Main entry point to the crawler command-line usage.
 */
@Command(
    name = "<app>",
    description = "%nOptions:",
    descriptionHeading = "%n<app> is the executable program used to "
            + "launch me%n",
    sortOptions = false,
    separator = " ",
    commandListHeading = "%nCommands:%n",
    footerHeading = "%nExamples:%n",
    footer = """
        %n  Start a crawl session:%n\
        %n    <app> start -config=/path/to/config.xml%n\
        %n  Stop a crawl session:%n\
        %n    <app> stop -config=/path/to/config.xml%n\
        %n  Get usage help on "check" command:%n\
        %n    <app> help configcheck%n
        """,
    subcommands = {
        HelpCommand.class,
        StartCommand.class,
        StopCommand.class,
        ConfigCheckCommand.class,
        ConfigRenderCommand.class,
        CleanCommand.class,
        StoreExportCommand.class,
        StoreImportCommand.class
    }
)
@EqualsAndHashCode
@ToString
public class MainCommand
        implements Callable<Integer>, IExecutionExceptionHandler {

    @NonNull private final CrawlSessionBuilder crawlSessionBuilder;

    @Option(
        names = {"-h", "-help"},
        usageHelp = true,
        description = "Show this help message and exit"
    )
    private boolean help;
    @Option(
        names = {"-v", "-version"},
        description = "Show the crawler version and exit"
    )
    private boolean version;

    @Spec
    private CommandSpec spec;

    public MainCommand(@NonNull CrawlSessionBuilder crawlSessionBuilder) {
        this.crawlSessionBuilder = crawlSessionBuilder;
    }

    CrawlSessionBuilder getCrawlSessionBuilder() {
        return crawlSessionBuilder;
    }

    @Override
    public Integer call() throws Exception {
        if (version) {
            spec.commandLine().getOut().println(
                    CrawlSession.getReleaseInfo(
                            crawlSessionBuilder.crawlSessionConfig()));
        }
        return 0;
    }

    @Override
    public int handleExecutionException(Exception ex, CommandLine commandLine,
            ParseResult parseResult) throws Exception {
        if (ex instanceof PicocliException) {
            commandLine.getErr().println(ex.getMessage());
            commandLine.getErr().println();
            commandLine.usage(commandLine.getErr());
            return -1;
        }
        throw ex;
    }
}
