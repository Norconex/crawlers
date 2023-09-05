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
import java.util.EventListener;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import com.norconex.committer.core.Committer;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.BeanMapperBuilder;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.crawler.core.checksum.DocumentChecksummer;
import com.norconex.crawler.core.checksum.MetadataChecksummer;
import com.norconex.crawler.core.crawler.ReferencesProvider;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.filter.DocumentFilter;
import com.norconex.crawler.core.filter.MetadataFilter;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.processor.DocumentProcessor;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.importer.Importer;

import jakarta.validation.ConstraintViolationException;
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
    private BeanMapper beanMapper;

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
    protected BeanMapper getBeanMapper() {
        return beanMapper;
    }

    protected abstract void runCommand();

    protected int createCrawlSession() {
        if (getConfigFile() == null || !getConfigFile().toFile().isFile()) {
            printErr("Configuration file does not exist or is not valid: "
                    + getConfigFile().toFile().getAbsolutePath());
            return -1;
        }

        beanMapper = beanMapper();

        var returnValue = configLoader();
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

    private int configLoader() {
        var cfg = getCrawlSessionConfig();
        try {
            ConfigurationLoader.builder()
                    .variablesFile(getVariablesFile())
                    .beanMapper(beanMapper)
                    .build()
                    .toObject(getConfigFile(), cfg);
        } catch (ConstraintViolationException e) {
            if (!e.getConstraintViolations().isEmpty()) {
                printErr();
                printErr(e.getConstraintViolations().size()
                        + " configuration errors detected:");
                printErr();
                e.getConstraintViolations().forEach(
                        cv -> printErr(cv.getMessage()));
                return  -1;
            }
        }
        return 0;
    }



    //TODO apply crawler defaults....

    //TODO move the crawler-specific BeamMapper initialization out
    // so it can be used outside file-loading context.

    private BeanMapper beanMapper() {
        var builder = BeanMapper.builder()
            //MAYBE: make package configurable? Maybe use java service loaded?
            .unboundPropertyMapping("crawlerDefaults",
                    parent.getCrawlSessionBuilder().crawlerConfigClass())
            .unboundPropertyMapping("crawler",
                    parent.getCrawlSessionBuilder().crawlerConfigClass())
            .unboundPropertyMapping("importer", Importer.class);

        registerPolymorpicTypes(builder);

        var beanMapperCustomizer =
                parent.getCrawlSessionBuilder().beanMapperCustomizer();
        if (beanMapperCustomizer != null) {
            beanMapperCustomizer.accept(builder);
        }
        return builder.build();
    }

    private void registerPolymorpicTypes(BeanMapperBuilder builder) {
        //TODO make scanning path configurable? Like java service loader?
        // or rely on fully qualified names for non Nx classes? Maybe the latter
        // is best to avoid name collisions?
        Predicate<String> predicate = nm -> nm.startsWith("com.norconex.");

        // This one has too many that are not meant to be added as configuration
        // so we only accept those that are standalone listeners:
        builder.polymorphicType(EventListener.class,
                predicate.and(nm -> nm.endsWith("EventListener")));
        builder.polymorphicType(ReferencesProvider.class, predicate);
        builder.polymorphicType(DataStoreEngine.class, predicate);
        builder.polymorphicType(ReferenceFilter.class, predicate);
        builder.polymorphicType(MetadataFilter.class, predicate);
        builder.polymorphicType(DocumentFilter.class, predicate);
        builder.polymorphicType(DocumentProcessor.class, predicate);
        builder.polymorphicType(MetadataChecksummer.class, predicate);
        builder.polymorphicType(Committer.class, predicate);
        builder.polymorphicType(DocumentChecksummer.class, predicate);
        builder.polymorphicType(SpoiledReferenceStrategizer.class, predicate);
        builder.polymorphicType(Fetcher.class, predicate);

        //TODO add importer dynamically somehow?  Maybe by adding
        // an unboundPropertyFactory, passing what it takes to load it?

  }
}
