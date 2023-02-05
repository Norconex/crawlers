/* Copyright 2014-2022 Norconex Inc.
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

import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XMLValidationException;
import com.norconex.importer.doc.Doc;
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
        super();
    }

    public static void launch(String[] args) {
        CommandLine cmd = parseCommandLineArguments(args);

        Path varFile = null;
        Path configFile = null;

        // Validate arguments
        if (cmd.hasOption(ARG_VARIABLES)) {
            varFile = Paths.get(cmd.getOptionValue(ARG_VARIABLES));

            if (!Files.isRegularFile(varFile)) {
                System.err.println("Invalid variable file path: "
                        + varFile.toAbsolutePath());
                System.exit(-1);
            }
        }
        if (cmd.hasOption(ARG_CONFIG)) {
            configFile = Paths.get(cmd.getOptionValue(ARG_CONFIG));
            if (!Files.isRegularFile(configFile)) {
                System.err.println("Invalid configuration file path: "
                        + configFile.toAbsolutePath());
                System.exit(-1);
            }
        }

        if (cmd.hasOption(ARG_CHECKCFG)) {
            checkConfig(configFile, varFile);
            return;
        }


        // Proceed
        ContentType contentType =
                ContentType.valueOf(cmd.getOptionValue(ARG_CONTENTTYPE));
        String contentEncoding = cmd.getOptionValue(ARG_CONTENTENCODING);
        String output = cmd.getOptionValue(ARG_OUTPUTFILE);
        if (StringUtils.isBlank(output)) {
            output = cmd.getOptionValue(ARG_INPUTFILE) + "-imported.txt";
        }
        String reference = cmd.getOptionValue(ARG_REFERENCE);
        Properties metadata = new Properties();
        ImporterConfig config =
                loadCommandLineConfig(cmd, configFile, varFile);
        Path inputFile = Paths.get(cmd.getOptionValue(ARG_INPUTFILE));
        try {
            ImporterResponse response = new Importer(config).importDocument(
                    new ImporterRequest(inputFile)
                        .setContentType(contentType)
                        .setContentEncoding(contentEncoding)
                        .setMetadata(metadata)
                        .setReference(reference));
            writeResponse(response, output,
                    cmd.getOptionValue(ARG_OUTMETAFORMAT), 0, 0);
        } catch (Exception e) {
            System.err.println(
                    "A problem occured while importing " + inputFile);
            e.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    private static ImporterConfig loadCommandLineConfig(
            CommandLine cmd, Path configFile, Path varFile) {
        if (configFile == null) {
            return null;
        }

        ImporterConfig config = new ImporterConfig();
        try {
            new ConfigurationLoader()
                .setVariablesFile(varFile)
                .loadFromXML(configFile, config);
        } catch (Exception e) {
            System.err.println("A problem occured loading configuration.");
            e.printStackTrace(System.err);
            System.exit(-1);
        }
        return config;
    }

    private static void checkConfig(Path configFile, Path varFile) {
        try {
            new ConfigurationLoader()
                    .setVariablesFile(varFile)
                    .loadFromXML(configFile, ImporterConfig.class);
            System.out.println("No XML configuration errors.");
        } catch (XMLValidationException e) {
            System.err.println("There were " + e.getErrors().size()
                    + " XML configuration error(s).");
            System.exit(-1);
        }
    }

    private static void writeResponse(ImporterResponse response,
            String outputPath, String outputFormat, int depth, int index) {
        if (!response.isSuccess()) {
            String statusLabel = "REJECTED: ";
            if (response.getImporterStatus().isError()) {
                statusLabel = "   ERROR: ";
            }
            System.out.println(statusLabel + response.getReference() + " ("
                    + response.getImporterStatus().getDescription() + ")");
        } else {
            Doc doc = response.getDocument();
            StringBuilder path = new StringBuilder(outputPath);
            if (depth > 0) {
                int pathLength = outputPath.length();
                int extLength = FilenameUtils.getExtension(outputPath).length();
                if (extLength > 0) {
                    extLength++;
                }
                String nameSuffix = "_" + depth + "-" + index;
                path.insert(pathLength - extLength, nameSuffix);
            }
            File docfile = new File(path.toString());

            try (FileOutputStream docOutStream = new FileOutputStream(docfile);
                 CachedInputStream docInStream = doc.getInputStream()) {
                // Write document file
                IOUtils.copy(docInStream, docOutStream);
                // Write metadata file
                MetaFileWriter.of(outputFormat).writeMeta(
                        doc.getMetadata(), docfile);
                System.out.println("IMPORTED: " + response.getReference());
            } catch (IOException e) {
                System.err.println(
                        "Could not write: " + doc.getReference());
                e.printStackTrace(System.err);
                System.err.println();
                System.err.flush();
            }
        }

        ImporterResponse[] nextedResponses = response.getNestedResponses();
        for (int i = 0; i < nextedResponses.length; i++) {
            ImporterResponse nextedResponse = nextedResponses[i];
            writeResponse(nextedResponse, outputPath,
                    outputFormat, depth + 1, i + 1);
        }
    }

    private static CommandLine parseCommandLineArguments(String[] args) {
        Options options = new Options();
        options.addOption("i", ARG_INPUTFILE, true,
                "File to be imported (required unless \"checkcfg\" is used).");
        options.addOption("o", ARG_OUTPUTFILE, true,
                "Optional: File where the imported content will be stored.");
        options.addOption("f", ARG_OUTMETAFORMAT, true,
                "Optional: File format for extracted metadata fields. "
              + "One of \"properties\" (default), \"json\", or \"xml\"");
        options.addOption("t", ARG_CONTENTTYPE, true,
                "Optional: The MIME Content-type of the input file.");
        options.addOption("e", ARG_CONTENTENCODING, true,
                "Optional: The content encoding (charset) of the input file.");
        options.addOption("r", ARG_REFERENCE, true,
                "Optional: Alternate unique qualifier for the input file "
              + "(e.g. URL).");
        options.addOption("c", ARG_CONFIG, true,
                "Optional: Importer XML configuration file.");
        options.addOption("v", ARG_VARIABLES, true,
                "Optional: variable file.");
        options.addOption("k", ARG_CHECKCFG, false,
                "Validates XML configuration without executing the Importer.");
        options.addOption("s", ARG_IGNOREERRORS, false,
                "Optional: Skip/ignore configuration validation errors "
              + "(if possible).");

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        try {
            cmd = parser.parse(options, args);
            if(!cmd.hasOption(ARG_INPUTFILE)
                    && !(cmd.hasOption(ARG_CHECKCFG)
                            && cmd.hasOption(ARG_CONFIG))) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "importer[.bat|.sh]", options );
                System.exit(-1);
            }
        } catch (ParseException e) {
            System.err.println("A problem occured while parsing arguments.");
            e.printStackTrace(System.err);
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp( "importer[.bat|.sh]", options );
            System.exit(-1);
        }
        return cmd;
    }

    private enum MetaFileWriter {
        JSON((meta, out) -> meta.storeToJSON(out)),
        XML((meta, out) -> meta.storeToXML(out)),
        PROPERTIES((meta, out) -> meta.storeToProperties(out));
        private FailableBiConsumer<Properties, OutputStream, IOException> c;
        MetaFileWriter(
                FailableBiConsumer<Properties, OutputStream, IOException> c) {
            this.c = c;
        }
        private void writeMeta(Properties meta, File file)
                throws IOException {
            try (FileOutputStream metaOut = new FileOutputStream(
                    file.getAbsolutePath() + "." + this.name().toLowerCase())) {
                c.accept(meta, metaOut);
            }
        }
        static final MetaFileWriter of(String outputFormat) {
            return Arrays.stream(values())
                .filter(fw -> fw.name().equalsIgnoreCase(outputFormat))
                .findFirst().orElse(PROPERTIES);
        }
    }
}
