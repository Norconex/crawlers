/* Copyright 2019-2024 Norconex Inc.
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

import com.norconex.commons.lang.ClassUtil;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.util.About;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import picocli.CommandLine.Command;
import picocli.CommandLine.HelpCommand;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * Main entry point to the crawler command-line usage.
 */
@Command(
    name = "<app>",
    description = "%nOptions:",
    descriptionHeading = "%n<app> is the program you ran.%n",
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
            CliStart.class,
            CliStop.class,
            CliConfigCheck.class,
            CliConfigRender.class,
            CliClean.class,
            CliStoreExport.class,
            CliStoreImport.class
    }
)
@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class CliRunner implements Runnable {
    //
    //    @NonNull
    //    private final CrawlerImpl crawlerImpl;
    //      @Getter
    //      @NonNull
    //      private final Crawler crawler;
    @Getter
    @NonNull
    private final Class<? extends CrawlerSpecProvider> specProviderClass;
    //    private final CrawlerConfig crawlerConfig; //TODO pass Class instead? Or builder with Class on it?

    //    @NonNull
    //    private final BeanMapper beanMapper;
    //
    @Option(
        names = { "-h", "-help" },
        usageHelp = true,
        description = "Show this help message and exit"
    )
    private boolean help;
    @Option(
        names = { "-v", "-version" },
        description = "Show the crawler version and exit"
    )
    private boolean version;

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        if (version) {

            ClassUtil.newInstance(specProviderClass).get();
            About.about(null);

            //            TaskContext.create(crawlerBuilderFactoryClass, b -> {
            //                spec.commandLine().getOut().println(
            //                        About.about(b.configuration(), false));
            //            });
        }
    }

    //    @Override
    //    public int handleExecutionException(
    //            Exception ex, CommandLine commandLine, ParseResult parseResult)
    //                    throws Exception {
    //        if (ex instanceof PicocliException) {
    //            commandLine.getErr().println(ex.getMessage());
    //            commandLine.getErr().println();
    //            commandLine.usage(commandLine.getErr());
    //            return -1;
    //        }
    //        throw ex;
    //    }
}
