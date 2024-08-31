/* Copyright 2023 Norconex Inc.
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
package com.norconex.cfgconverter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

import com.norconex.cfgconverter.json.XmlV4ToJsonV4ConfigConverter;
import com.norconex.cfgconverter.xml.XmlToXmlV4ConfigConverter;
import com.norconex.cfgconverter.yaml.XmlV4ToYamlV4ConfigConverter;
import com.norconex.commons.lang.xml.Xml;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.PicocliException;
import picocli.CommandLine.Spec;

/**
 * Launches the configuration converter from the command-line.
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
        %n  Convert old XML configuration to YAML:%n\
        %n    <app> start -i=/path/to/input.xml -o=/path/to/output.yaml%n\
        %n  Convert old XML configuration with explicit target format:%n\
        %n    <app> start -i=/path/to/input.xml -o=/path/to/output.config \
        -f=json%n
        """
)
@EqualsAndHashCode
@ToString
public class ConfigConverterLauncher
        implements Callable<Integer>, IExecutionExceptionHandler {

    private enum Format {
        xml(XmlToXmlV4ConfigConverter::new), //NOSONAR
        yaml(XmlV4ToYamlV4ConfigConverter::new), //NOSONAR
        json(XmlV4ToJsonV4ConfigConverter::new)  //NOSONAR
        ;
        final Supplier<ConfigConverter> converterSupplier;
        Format(Supplier<ConfigConverter> converterSupplier) {
            this.converterSupplier = converterSupplier;
        }
    }

    @Option(
        names = {"-h", "-help"},
        usageHelp = true,
        description = "Show this help message and exit"
    )
    private boolean help;

    @Option(
        names = { "-i", "-input" },
        description = "Input XML configuration file.",
        required = true
    )
    private Path inputFile;

    @Option(
        names = { "-o", "-output" },
        description = "Output file path.",
        required = true
    )
    private Path outputFile;

    @Option(
        names = { "-f", "-format" },
        description = "Configuration format of target file:"
                + " ${COMPLETION-CANDIDATES}. Default is derived from output "
                + "file extension, or \"yaml\" if it can'be resolved.",
        required = false
    )
    private Optional<Format> format;

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        if (!Files.isRegularFile(inputFile)) {
            System.err.println( //NOSONAR
                    "%s does not exist or is not a valid file."
                    .formatted(inputFile));
        }
        var fmt = format.orElseGet(() -> {
            var path = outputFile.toString().toLowerCase();
            if (path.endsWith(".xml")) {
                return Format.xml;
            }
            if (path.endsWith(".json")) {
                return Format.json;
            }
            return Format.yaml;
        });

        var xml = new Xml(inputFile);
        try (var writer = Files.newBufferedWriter(outputFile)) {

            fmt.converterSupplier.get().convert(xml, writer);
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

    public static void main(String[] args) {
        try {
            System.exit(launch(args));
        } catch (Exception e) {
            e.printStackTrace(System.err); //NOSONAR
            System.exit(1);
        }
    }

    public static int launch(String... args) {
        var cfgConverter = new ConfigConverterLauncher();
        var cmdLine = new CommandLine(cfgConverter);
        cmdLine.setExecutionExceptionHandler(cfgConverter);
        if (args.length == 0) {
            cmdLine.getErr().println("No arguments provided.");
            cmdLine.usage(cmdLine.getOut());
            return -1;
        }
        return cmdLine.execute(args);
    }
}
