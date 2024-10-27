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

import java.io.PrintWriter;
import java.nio.file.Path;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerSpec;

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
        var spec = ClassUtil.newInstance(parent.getSpecProviderClass()).get();
        var crawlerContext = new CrawlerContext(
                parent.getSpecProviderClass(), loadConfiguration(spec));
        //        loadConfiguration(crawlerContext);
        runCommand(new Crawler(crawlerContext));
    }

    protected abstract void runCommand(Crawler crawler);

    protected PrintWriter out() {
        return spec.commandLine().getOut();
    }

    protected PrintWriter err() {
        return spec.commandLine().getErr();
    }

    private CrawlerConfig loadConfiguration(CrawlerSpec spec) {
        if (getConfigFile() == null || !getConfigFile().toFile().isFile()) {
            throw new CliException(
                    "Configuration file does not exist or is not valid: "
                            + getConfigFile().toFile().getAbsolutePath());
        }
        //        var cfg = ClassUtil.newInstance(spec.crawlerConfigClass());
        //        var cfg = crawlerContext.getConfiguration();
        try {
            return ConfigurationLoader
                    .builder()
                    .variablesFile(getVariablesFile())
                    .beanMapper(spec.beanMapper())
                    .build()
                    .toObject(getConfigFile(), spec.crawlerConfigClass());
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
