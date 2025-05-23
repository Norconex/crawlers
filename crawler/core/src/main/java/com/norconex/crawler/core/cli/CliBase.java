/* Copyright 2019-2025 Norconex Inc.
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

import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.Crawler;

import jakarta.validation.ConstraintViolationException;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;

/**
 * Base class for sub-commands.
 */
@EqualsAndHashCode
@ToString
public abstract class CliBase implements Runnable {

    @ParentCommand
    private CliRunner parent;

    @Spec
    private CommandSpec spec;

    @Option(
        names = { "-c", "-config" },
        paramLabel = "FILE",
        description = "Path to crawl session configuration file.",
        required = true
    )
    @Getter
    @Setter
    private Path configFile;

    @Option(
        names = { "-variables" },
        paramLabel = "FILE",
        description = "Path to variables file."
    )
    @Getter
    @Setter
    private Path variablesFile;

    @Override
    public void run() {
        var driver = parent.getCrawlDriver();
        runCommand(new Crawler(driver, loadConfiguration(driver)));
    }

    protected abstract void runCommand(Crawler crawler);

    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    private CrawlConfig loadConfiguration(CrawlDriver driver) {
        if (getConfigFile() == null
                || !Files.exists(getConfigFile())
                || !getConfigFile().toFile().isFile()) {
            throw new CliException(
                    "Configuration file does not exist or is not valid: "
                            + getConfigFile().toFile().getAbsolutePath());
        }
        try {
            return ConfigurationLoader
                    .builder()
                    .variablesFile(getVariablesFile())
                    .beanMapper(driver.beanMapper())
                    .build()
                    .toObject(getConfigFile(), driver.crawlerConfigClass());
        } catch (ConstraintViolationException e) {
            if (!e.getConstraintViolations().isEmpty()) {
                var b = new StringBuilder();
                b.append(e.getConstraintViolations().size()
                        + " configuration errors detected:\n");
                e.getConstraintViolations().forEach(
                        cv -> b
                                .append("\"")
                                .append(cv.getPropertyPath())
                                .append("\" ")
                                .append(cv.getMessage())
                                .append(". Invalid value: ")
                                .append(cv.getInvalidValue())
                                .append(".\n"));
                throw new CliException(b.toString());
            }
            throw e;
        }
    }
}
