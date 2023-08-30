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
import java.util.Set;
import java.util.concurrent.Callable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.xml.ErrorHandlerCapturer;
import com.norconex.crawler.core.crawler.CrawlerException;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import picocli.CommandLine;
import picocli.CommandLine.Help.Visibility;
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

    public enum ConfigVersion { PRE_V4, V4 }

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

    @Option(
        names = {"-cfgversion"},
        paramLabel = "[PRE_V4|V4] (Default: ${DEFAULT-VALUE})",
        description = "Version originaly targetted by configuration file.",
        defaultValue = "PRE_V4",
        showDefaultValue = Visibility.ON_DEMAND
    )
    private ConfigVersion configVersion = ConfigVersion.PRE_V4;

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

        //TODO Support both config versions via a flag for now... until we
        // figure out the final approach.
        int returnValue;
        if (configVersion == ConfigVersion.PRE_V4) {
            returnValue = preV4ConfigLoader();
        } else {
            returnValue = v4ConfigLoader(); // default
        }
        if (returnValue != 0) {
            return returnValue;
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
        runCommand();
        return 0;
    }

    private int v4ConfigLoader() {
        //TODO for now, we default to XML if we can't derive from file
        // extension.
        var path = getConfigFile().toString().toLowerCase();
        ObjectMapper mapper;
        if (path.endsWith(".json")) {
            mapper = new ObjectMapper();
        } else if (path.endsWith(".yaml") || path.endsWith(".yml")) {
            mapper = new YAMLMapper();
        } else {
            mapper = new XmlMapper();
        }

        var cfg = getCrawlSessionConfig();

        var cfgText = new ConfigurationLoader()
            .setVariablesFile(getVariablesFile())
            .loadString(getConfigFile());

        try {
            mapper.readerForUpdating(cfg).readValue(cfgText);
        } catch (JsonProcessingException e) {
            throw new CrawlerException("Could not parse config file: "
                    + getConfigFile().toAbsolutePath().toString(), e);
        }

        var factory = Validation.buildDefaultValidatorFactory();
        var validator = factory.getValidator();
        Set<ConstraintViolation<CrawlSessionConfig>> violations =
                validator.validate(cfg);

        if (!violations.isEmpty()) {
            printErr();
            printErr(violations.size() + " configuration errors detected:");
            printErr();
            violations.forEach(cv -> printErr(cv.getMessage()));
            return  -1;
        }
        return 0;
    }

    private int preV4ConfigLoader() {
        //TODO convert from XMLv3 to XMLv4 first... then call 
        // v4ConfigLoader
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
        return 0;
    }

    protected abstract void runCommand();
}
