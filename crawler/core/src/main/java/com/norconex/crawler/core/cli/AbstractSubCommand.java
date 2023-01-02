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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.xml.ErrorHandlerCapturer;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Base class for sub-commands.
 */
@EqualsAndHashCode
@ToString
public abstract class AbstractSubCommand implements Callable<Integer> {

    @ParentCommand
    private MainCommand parent;

    @Spec
    private CommandSpec spec;

    @Option(
        names = {"-c", "-config"},
        paramLabel = "FILE",
        description = "Path to crawl session configuration file.",
        required = true
    )
    @Getter @Setter
    private Path configFile;

    @Option(
        names = {"-variables"},
        paramLabel = "FILE",
        description = "Path to variables file."
    )
    @Getter @Setter
    private Path variablesFile;

    @Option(
        names = {"-crawlers"},
        paramLabel = "<crawler>",
        description = "TO IMPLEMENT: Restrict the command to one or more "
                + "crawler (comma-separated).",
        split = ","
    )
    @Getter
    private final List<String> crawlers = new ArrayList<>();

    private CrawlSession crawlSession;

    protected void printOut() {
        commandLine().getOut().println();
    }
    protected void printOut(String str) {
        commandLine().getOut().println(str);
    }
    protected void printErr() {
        commandLine().getErr().println();
    }
    protected void printErr(String str) {
        commandLine().getErr().println(str);
    }
    protected CommandLine commandLine() {
        return spec.commandLine();
    }
    protected CrawlSession getCrawlSession() {
       return crawlSession;
    }
    protected CrawlSessionConfig getCrawlSessionConfig() {
        return parent.getCrawlSessionBuilder().crawlSessionConfig();
    }

    protected int createCrawlSession() {
        if (getConfigFile() == null || !getConfigFile().toFile().isFile()) {
            printErr("Configuration file does not exist or is not valid: "
                    + getConfigFile().toFile().getAbsolutePath());
            return -1;
        }
        var eh = new ErrorHandlerCapturer(getClass());
        new ConfigurationLoader()
                .setVariablesFile(getVariablesFile())
                .loadFromXML(getConfigFile(), getCrawlSessionConfig(), eh);
        if (!eh.getErrors().isEmpty()) {
            printErr();
            printErr(eh.getErrors().size()
                    + " XML configuration errors detected:");
            printErr();
            eh.getErrors().stream().forEach(er ->
                    printErr(er.getMessage()));
            return  -1;
        }

        crawlSession = parent.getCrawlSessionBuilder().build();

        return 0;
    }

    @Override
    public Integer call() throws Exception {
        var exitVal = createCrawlSession();
        if (exitVal != 0) {
            return exitVal;
        }
//        try {
            runCommand();
            return 0;
//        } catch (Exception e) {
//            throw new CrawlSessionException(
//                    "Could not execute crawl command.", e);
//        }
    }


//    @Override
//    public Integer call() throws Exception {
//        var exitVal = createCrawlSession();
//        if (exitVal != 0) {
//            return exitVal;
//        }
//        try {
//            runCommand();
//            return 0;
//        } catch (Exception e) {
////            if (e instanceof NullPointerException) {// && e.getMessage() == null) {
////                //TODO maybe cache message and if blank always print
////                //stacktrace regardless of exception type?
////                printErr("ERROR: " + ExceptionUtils.getStackTrace(e));
////            } else {
//                printErr("ERROR: " + ExceptionUtil.getFormattedMessages(e));
////                printErr("ERROR: " + msg);
////                var mi = new MutableInt();
////                ExceptionUtils.getThrowableList(e).forEach(ex -> {
////                    var i = mi.getAndIncrement();
////                    var msg = ex.getLocalizedMessage();
////                    if (i == 0) {
////                        printErr("ERROR: " + msg);
////                    } else {
////                        printErr(StringUtils.repeat(' ', i * 2) + "→ " + msg);
////                    }
////                });
////            }
//            return -1;
//        }
//    }

    protected abstract void runCommand();
}
