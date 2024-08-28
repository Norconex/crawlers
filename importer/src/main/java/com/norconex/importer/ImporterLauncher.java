/* Copyright 2014-2024 Norconex Inc.
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
package com.norconex.importer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.FailableBiConsumer;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XMLValidationException;
import com.norconex.importer.response.ImporterResponse;

/**
 * Command line launcher of the Importer application.  Invoked by the
 * {@link Importer#main(String[])} method.
 */
public final class ImporterLauncher {

    private static final String ARG_INPUTFILE = "inputFile";
    private static final String ARG_OUTPUTFILE = "outputFile";
    private static final String ARG_CONTENTTYPE = "contentType";
    private static final String ARG_OUTMETAFORMAT = "outputMetaFormat";
    private static final String ARG_CONTENTENCODING = "contentEncoding";
    private static final String ARG_REFERENCE = "reference";
    private static final String ARG_CONFIG = "config";
    public static final String ARG_VARIABLES = "variables";
    public static final String ARG_CHECKCFG = "checkcfg";
    public static final String ARG_IGNOREERRORS = "ignoreErrors";

    /**
     * Constructor.
     */
    private ImporterLauncher() {
    }

    public static int launch(String[] args) {
        var cmd = parseCommandLineArguments(args);

        if (cmd == null) {
            return -1;
        }

        Path varFile = null;
        Path configFile = null;

        // Validate arguments
        if (cmd.hasOption(ARG_VARIABLES)) {
            varFile = Paths.get(cmd.getOptionValue(ARG_VARIABLES));

            if (!Files.isRegularFile(varFile)) {
                err().println("Invalid variable file path: "
                        + varFile.toAbsolutePath());
                return -1;
            }
        }
        if (cmd.hasOption(ARG_CONFIG)) {
            configFile = Paths.get(cmd.getOptionValue(ARG_CONFIG));
            if (!Files.isRegularFile(configFile)) {
                err().println("Invalid configuration file path: "
                        + configFile.toAbsolutePath());
                return -1;
            }
        }

        if (cmd.hasOption(ARG_CHECKCFG)) {
            return checkConfig(configFile, varFile);
        }

        // Proceed
        var contentType =
                ContentType.valueOf(cmd.getOptionValue(ARG_CONTENTTYPE));
        var contentEncoding = cmd.getOptionValue(ARG_CONTENTENCODING);
        var output = cmd.getOptionValue(ARG_OUTPUTFILE);
        if (StringUtils.isBlank(output)) {
            output = cmd.getOptionValue(ARG_INPUTFILE) + "-imported.txt";
        }
        var reference = cmd.getOptionValue(ARG_REFERENCE);
        var metadata = new Properties();
        var config = loadCommandLineConfig(configFile, varFile);
        if (config == null) {
            return -1;
        }

        var inputFile = Paths.get(cmd.getOptionValue(ARG_INPUTFILE));
        try {
            var response = new Importer(config).importDocument(
                    new ImporterRequest(inputFile)
                            .setContentType(contentType)
                            .setCharset(
                                    contentEncoding != null
                                            ? Charset.forName(contentEncoding)
                                            : null)
                            .setMetadata(metadata)
                            .setReference(reference));
            writeResponse(
                    response, output,
                    cmd.getOptionValue(ARG_OUTMETAFORMAT), 0, 0);
        } catch (Exception e) {
            err().println("A problem occured while importing " + inputFile);
            e.printStackTrace(err());
            return -1;
        }
        return 0;
    }

    private static ImporterConfig loadCommandLineConfig(
            Path configFile, Path varFile) {
        if (configFile == null) {
            return null;
        }

        var config = new ImporterConfig();
        try {
            ConfigurationLoader
                    .builder()
                    .variablesFile(varFile)
                    .build()
                    .toObject(configFile, config);
        } catch (Exception e) {
            err().println("A problem occured loading configuration.");
            e.printStackTrace(err());
            return null;
        }
        return config;
    }

    private static int checkConfig(Path configFile, Path varFile) {
        try {
            ConfigurationLoader
                    .builder()
                    .variablesFile(varFile)
                    .build()
                    .toObject(configFile, ImporterConfig.class);
            out().println("No XML configuration errors.");
        } catch (XMLValidationException e) {
            err().println(
                    "There were " + e.getErrors().size()
                            + " XML configuration error(s).");
            return -1;
        } catch (Exception e) {
            err().println(
                    "Could not parse configuration file. Error: "
                            + ExceptionUtil.getFormattedMessages(e));
            return -1;
        }
        return 0;
    }

    private static void writeResponse(
            ImporterResponse response,
            String outputPath, String outputFormat, int depth, int index) {
        if (!response.isSuccess()) {
            var statusLabel = "REJECTED: ";
            if (response.isError()) {
                statusLabel = "   ERROR: ";
            }
            out().println(
                    statusLabel + response.getReference() + " ("
                            + response.getDescription() + ")");
        } else {
            var doc = response.getDoc();
            var path = new StringBuilder(outputPath);
            if (depth > 0) {
                var pathLength = outputPath.length();
                var extLength = FilenameUtils.getExtension(outputPath).length();
                if (extLength > 0) {
                    extLength++;
                }
                var nameSuffix = "_" + depth + "-" + index;
                path.insert(pathLength - extLength, nameSuffix);
            }
            var docfile = new File(path.toString());

            try (var docOutStream = new FileOutputStream(docfile);
                    var docInStream = doc.getInputStream()) {
                // Write document file
                IOUtils.copy(docInStream, docOutStream);
                // Write metadata file
                MetaFileWriter.of(outputFormat).writeMeta(
                        doc.getMetadata(), docfile);
                out().println("IMPORTED: " + response.getReference());
            } catch (IOException e) {
                err().println("Could not write: " + doc.getReference());
                e.printStackTrace(err());
                err().println();
                err().flush();
            }
        }

        var nextedResponses = response.getNestedResponses();
        for (var i = 0; i < nextedResponses.size(); i++) {
            var nextedResponse = nextedResponses.get(i);
            writeResponse(
                    nextedResponse, outputPath,
                    outputFormat, depth + 1, i + 1);
        }
    }

    private static CommandLine parseCommandLineArguments(String[] args) {
        var options = new Options();
        options.addOption(
                "i", ARG_INPUTFILE, true,
                "File to be imported (required unless \"checkcfg\" is used).");
        options.addOption(
                "o", ARG_OUTPUTFILE, true,
                "Optional: File where the imported content will be stored.");
        options.addOption(
                "f", ARG_OUTMETAFORMAT, true,
                """
                Optional: File format for extracted metadata fields. \
                One of "properties" (default), "json", \
                or "xml\"""");
        options.addOption(
                "t", ARG_CONTENTTYPE, true,
                "Optional: The MIME Content-type of the input file.");
        options.addOption(
                "e", ARG_CONTENTENCODING, true,
                "Optional: The content encoding (charset) of the input file.");
        options.addOption(
                "r", ARG_REFERENCE, true,
                "Optional: Alternate unique qualifier for the input file "
                        + "(e.g. URL).");
        options.addOption(
                "c", ARG_CONFIG, true,
                "Optional: Importer XML configuration file.");
        options.addOption(
                "v", ARG_VARIABLES, true,
                "Optional: variable file.");
        options.addOption(
                "k", ARG_CHECKCFG, false,
                "Validates XML configuration without executing the Importer.");
        options.addOption(
                "s", ARG_IGNOREERRORS, false,
                "Optional: Skip/ignore configuration validation errors "
                        + "(if possible).");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
            if (!cmd.hasOption(ARG_INPUTFILE)
                    && (!cmd.hasOption(ARG_CHECKCFG)
                            || !cmd.hasOption(ARG_CONFIG))) {
                var formatter = new HelpFormatter();
                formatter.printHelp("importer[.bat|.sh]", options);
                return null;
            }
        } catch (ParseException e) {
            err().println("A problem occured while parsing arguments.");
            e.printStackTrace(err());
            var formatter = new HelpFormatter();
            formatter.printHelp("importer[.bat|.sh]", options);
            return null;
        }
        return cmd;
    }

    private enum MetaFileWriter {
        JSON(Properties::storeToJSON),
        XML(Properties::storeToXML),
        PROPERTIES(Properties::storeToProperties);

        private FailableBiConsumer<Properties, OutputStream, IOException> c;

        MetaFileWriter(
                FailableBiConsumer<Properties, OutputStream, IOException> c) {
            this.c = c;
        }

        private void writeMeta(Properties meta, File file)
                throws IOException {
            try (var metaOut = new FileOutputStream(
                    file.getAbsolutePath() + "." + name().toLowerCase())) {
                c.accept(meta, metaOut);
            }
        }

        static final MetaFileWriter of(String outputFormat) {
            return Arrays.stream(values())
                    .filter(fw -> fw.name().equalsIgnoreCase(outputFormat))
                    .findFirst().orElse(PROPERTIES);
        }
    }

    private static PrintStream err() {
        return System.err; //NOSONAR
    }
    private static PrintStream out() {
        return System.out; //NOSONAR
    }
}
