/* Copyright 2018-2022 Norconex Inc.
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
package com.norconex.importer.handler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.exec.SystemCommand;
import com.norconex.commons.lang.exec.SystemCommandException;
import com.norconex.commons.lang.io.CachedStream;
import com.norconex.commons.lang.io.InputStreamLineListener;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.ImporterRuntimeException;
import com.norconex.importer.handler.tagger.impl.ExternalTagger;
import com.norconex.importer.handler.transformer.impl.ExternalTransformer;
import com.norconex.importer.parser.impl.ExternalParser;

/**
 * <p>
 * Class executing an external application
 * to extract data from and/or manipulate a document.
 * </p>
 *
 * <h3>Command-line arguments:</h3>
 * <p>
 * When constructing the command to launch the external application, it
 * will look for specific tokens to be replaced by file paths
 * arguments (in addition to other arguments you may have).
 * The path arguments are created by this class. They are case-sensitive and
 * the file they represent are temporary (will be deleted after
 * they have been dealt with). It is possible to omit one or more tokens to use
 * standard streams instead where applicable.
 * </p>
 * <p>
 * Tokens supported by this class are:
 * </p>
 * <dl>
 *
 *   <dt><code>${INPUT}</code></dt>
 *   <dd>Path to document to be handled by the external application.
 *       When omitted, the document content
 *       is sent to the external application using the standard input
 *       stream (STDIN).</dd>
 *
 *   <dt><code>${INPUT_META}</code></dt>
 *   <dd>Path to file containing metadata information available
 *       so far for the document to be handled by the external application.
 *       By default in JSON format. When omitted, no metadata will be made
 *       available to the external application.</dd>
 *
 *   <dt><code>${OUTPUT}</code></dt>
 *   <dd>Path to document resulting from this external handler.
 *       When omitted, the output content will be read from the external
 *       application standard output (STDOUT).</dd>
 *
 *   <dt><code>${OUTPUT_META}</code></dt>
 *   <dd>Path to file containing new metadata for the document.
 *       By default, the expected format is JSON.
 *       When omitted, any metadata extraction patterns defined will be
 *       applied against both the external program standard output (STDOUT)
 *       and standard error (STDERR). If no patterns are defined, it is
 *       assumed no new metadata resulted from the external application.</dd>
 *
 *   <dt><code>${REFERENCE}</code></dt>
 *   <dd>Unique reference to the document being handled
 *       (URL, original file system location, etc.). When omitted,
 *       the document reference will not be made available
 *       to the external application.</dd>
 *
 * </dl>
 *
 * <h3>Metadata file format:</h3>
 *
 * <p>
 * If <code>${INPUT_META}</code> is part of the command, metadata can be
 * provided to the external application in JSON (default), XML or
 * Properties format.  Those
 * formats can also be used if <code>${OUTPUT_META}</code> is part of the
 * command. The formats are:
 * </p>
 *
 * <h4>JSON</h4>
 * <pre><code class="language-json">
 * {
 *   "field1" : [ "value1a", "value1b", "value1c" ],
 *   "field2" : [ "value2" ],
 *   "field3" : [ "value3a", "value3b" ]
 * }
 * </code></pre>
 *
 * <h4>XML</h4>
 * <p>Java Properties XML file format, with the exception that
 * metadata with multiple values are supported, and will have their values
 * joined by the symbol for record separator (U+241E).
 * Example:
 * </p>
 * <pre><code class="language-xml">
 * &lt;?xml version="1.0" encoding="UTF-8"?&gt;
 * &lt;!DOCTYPE properties SYSTEM "http://java.sun.com/dtd/properties.dtd"&gt;
 * &lt;properties&gt;
 *   &lt;comment&gt;My Comment&lt;/comment&gt;
 *   &lt;entry key="field1"&gt;value1a\u241Evalue1b\u241Evalue1c&lt;/entry&gt;
 *   &lt;entry key="field2"&gt;value2&lt;/entry&gt;
 *   &lt;entry key="field3"&gt;value3a\u241Evalue3b&lt;/entry&gt;
 * &lt;/properties&gt;
 * </code></pre>
 *
 * <h4>Properties</h4>
 * <p>Java Properties standard file format, with the exception that
 * metadata with multiple values are supported, and will have their values
 * joined by the symbol for record separator (U+241E). Refer to Java
 * {@link Properties#loadFromProperties(java.io.Reader)} for
 * general syntax information.
 * Example:
 * </p>
 * <pre><code class="language-properties">
 *   # My Comment
 *   field1 = value1a\u241Evalue1b\u241Evalue1c
 *   field2 = value2
 *   field3 = value3a\u241Evalue3b
 * </code></pre>
 *
 * <h3>Metadata extraction patterns:</h3>
 * <p>
 * It is possible to specify metadata extraction patterns that will be
 * applied either on the returned metadata file or from the standard output and
 * error streams.  If <code>${OUTPUT_META}</code> is found in the command,
 * the output format will be
 * used to parse the outgoing metadata file. Leave the format to
 * <code>null</code> to rely on extraction patterns for parsing the output file.
 * </p>
 * <p>
 * When <code>${OUTPUT_META}</code> is omitted, extraction patterns will be
 * applied to
 * the external application standard output and standard error streams. If
 * there are no <code>${OUTPUT_META}</code> and no metadata extraction patterns
 * are defined, it is assumed the external application did not produce any new
 * metadata.
 * </p>
 * <p>
 * When using metadata extraction patterns with standard streams, each pattern
 * is applied on each line returned from STDOUT and STDERR.  With each pattern,
 * there could be a matadata field name supplied. If the pattern does not
 * contain any match group, the entire matched expression will be used as the
 * metadata field value.
 * </p>
 * <p>
 * Field names and values can be obtained by using the same regular
 * expression.  This is done by using
 * match groups in your regular expressions (parenthesis).  For each pattern
 * you define, you can specify which match group hold the field name and
 * which one holds the value.
 * Specifying a field match group is optional if a <code>field</code>
 * is provided.  If no match groups are specified, a <code>field</code>
 * is expected.
 * </p>
 *
 * <h3>Storing values in an existing field</h3>
 * <p>
 * If a target field with the same name already exists for a document,
 * values will be added to the end of the existing value list.
 * It is possible to change this default behavior by supplying a
 * {@link PropertySetter}.
 * </p>
 *
 * <h3>Environment variables:</h3>
 *
 * <p>
 * Execution environment variables can be set to replace environment variables
 * defined for the current process.
 * </p>
 *
 * <p>
 * To extract raw text from files, it is recommended to use an
 * {@link com.norconex.importer.parser.impl.ExternalParser} instead.
 * </p>
 *
 * {@nx.xml.usage
 * <command>
 *   /Apps/myapp.exe ${INPUT} ${OUTPUT} ${INPUT_META} ${OUTPUT_META} ${REFERENCE}
 * </command>
 *
 * <metadata
 *     inputFormat="[json|xml|properties]"
 *     outputFormat="[json|xml|properties]"
 *     {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *     <!-- Pattern only used when no output format is specified.
 *          Repeat as needed. -->
 *   <pattern {@nx.include com.norconex.commons.lang.text.RegexFieldValueExtractor#attributes}>
 *     (regular expression)
 *   </pattern>
 * </metadata>
 *
 * <environment>
 *   <!-- repeat variable tag as needed -->
 *   <variable name="(environment variable name)">
 *     (environment variable value)
 *   </variable>
 * </environment>
 *
 * <tempDir>
 *   (Optional directory where to store temporary files used
 *    by this class.)
 * </tempDir>
 * }
 * <p>Consuming classes implementing {@link XMLConfigurable} can use
 * the XML save/load methods of this class to inherit the above
 * (which they can support differently).</p>
 *
 * @see ExternalTagger
 * @see ExternalTransformer
 * @see ExternalParser
 */
@SuppressWarnings("javadoc")
public class ExternalHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ExternalHandler.class);

    public static final String TOKEN_INPUT = "${INPUT}";
    public static final String TOKEN_OUTPUT = "${OUTPUT}";
    public static final String TOKEN_INPUT_META = "${INPUT_META}";
    public static final String TOKEN_OUTPUT_META = "${OUTPUT_META}";
    public static final String TOKEN_REFERENCE = "${REFERENCE}";

    public static final String META_FORMAT_JSON = "json";
    public static final String META_FORMAT_XML = "xml";
    public static final String META_FORMAT_PROPERTIES = "properties";

    private String command;
    private final List<RegexFieldValueExtractor> patterns = new ArrayList<>();

    // Null means inherit from those of java process
    private Map<String, String> environmentVariables = null;

    private String metadataInputFormat = META_FORMAT_JSON;
    private String metadataOutputFormat = META_FORMAT_JSON;
    private Path tempDir;
    private PropertySetter onSet;

    /**
     * Gets the command to execute.
     * @return the command
     */
    public String getCommand() {
        return command;
    }
    /**
     * Sets the command to execute. Make sure to escape spaces in
     * executable path and its arguments as well as other special command
     * line characters.
     * @param command the command
     */
    public void setCommand(String command) {
        this.command = command;
    }

    /**
     * Gets directory where to store temporary files sent to the external
     * handler as file paths.
     * @return temporary directory
     */
    public Path getTempDir() {
        return tempDir;
    }
    /**
     * Sets directory where to store temporary files sent to the external
     * handler as file paths.
     * @param tempDir temporary directory
     */
    public void setTempDir(Path tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * Gets metadata extraction patterns. See class documentation.
     * @return map of patterns and field names
     */
    public List<RegexFieldValueExtractor> getMetadataExtractionPatterns() {
        return Collections.unmodifiableList(patterns);
    }
    /**
     * Adds a metadata extraction pattern that will extract the whole text
     * matched into the given field.
     * @param field target field to store the matching pattern.
     * @param pattern the pattern
     */
    public void addMetadataExtractionPattern(String field, String pattern) {
        if (StringUtils.isAnyBlank(pattern, field)) {
            return;
        }
        addMetadataExtractionPatterns(
                new RegexFieldValueExtractor(pattern).setToField(field));
    }
    /**
     * Adds a metadata extraction pattern, which will extract the value from
     * the specified group index upon matching.
     * @param field target field to store the matching pattern.
     * @param pattern the pattern
     * @param valueGroup which pattern group to return.
     */
    public void addMetadataExtractionPattern(
            String field, String pattern, int valueGroup) {
        if (StringUtils.isAnyBlank(pattern, field)) {
            return;
        }
        addMetadataExtractionPatterns(new RegexFieldValueExtractor(
                pattern).setToField(field).setValueGroup(valueGroup));
    }
    /**
     * Adds a metadata extraction pattern that will extract matching field
     * names/values.
     * @param patterns extraction pattern
     */
    public void addMetadataExtractionPatterns(
            RegexFieldValueExtractor... patterns) {
        if (ArrayUtils.isNotEmpty(patterns)) {
            this.patterns.addAll(Arrays.asList(patterns));
        }
    }
    /**
     * Sets metadata extraction patterns. Clears any previously assigned
     * patterns.
     * @param patterns extraction pattern
     */
    public void setMetadataExtractionPatterns(RegexFieldValueExtractor... patterns) {
        this.patterns.clear();
        addMetadataExtractionPatterns(patterns);
    }

    /**
     * Gets environment variables.
     * @return environment variables or <code>null</code> if using the current
     *         process environment variables
     */
    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }
    /**
     * Sets the environment variables. Clearing any prevously assigned
     * environment variables. Set <code>null</code> to use
     * the current process environment variables (default).
     * @param environmentVariables environment variables
     */
    public void setEnvironmentVariables(
            Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }
    /**
     * Adds the environment variables, keeping environment variables previously
     * assigned. Existing variables of the same name
     * will be overwritten. To clear all previously assigned variables and use
     * the current process environment variables, pass
     * <code>null</code> to
     * {@link ExternalTransformer#setEnvironmentVariables(Map)}.
     * @param environmentVariables environment variables
     */
    public void addEnvironmentVariables(
            Map<String, String> environmentVariables) {
        if (this.environmentVariables != null) {
            this.environmentVariables.putAll(environmentVariables);
        } else {
            this.environmentVariables = environmentVariables;
        }
    }
    /**
     * Adds an environment variables to the list of previously
     * assigned variables (if any). Existing variables of the same name
     * will be overwritten. Setting a variable with a
     * <code>null</code> name has no effect while <code>null</code>
     * values are converted to empty strings.
     * @param name environment variable name
     * @param value environment variable value
     */
    public void addEnvironmentVariable(String name, String value) {
        if (environmentVariables == null) {
            environmentVariables = new HashMap<>();
        }
        environmentVariables.put(name, value);
    }

    /**
     * Gets the format of the metadata input file sent to the external
     * application. One of "json" (default), "xml", or "properties" is expected.
     * Only applicable when the <code>${INPUT}</code> token
     * is part of the command.
     * @return metadata input format
     */
    public String getMetadataInputFormat() {
        return metadataInputFormat;
    }
    /**
     * Sets the format of the metadata input file sent to the external
     * application. One of "json" (default), "xml", or "properties" is expected.
     * Only applicable when the <code>${INPUT}</code> token
     * is part of the command.
     * @param metadataInputFormat format of the metadata input file
     */
    public void setMetadataInputFormat(String metadataInputFormat) {
        this.metadataInputFormat = metadataInputFormat;
    }
    /**
     * Gets the format of the metadata output file from the external
     * application. By default no format is set, and metadata extraction
     * patterns are used to extract metadata information.
     * One of "json", "xml", or "properties" is expected.
     * Only applicable when the <code>${OUTPUT}</code> token
     * is part of the command.
     * @return metadata output format
     */
    public String getMetadataOutputFormat() {
        return metadataOutputFormat;
    }
    /**
     * Sets the format of the metadata output file from the external
     * application. One of "json" (default), "xml", or "properties" is expected.
     * Set to <code>null</code> for relying metadata extraction
     * patterns instead.
     * Only applicable when the <code>${OUTPUT}</code> token
     * is part of the command.
     * @param metadataOutputFormat format of the metadata output file
     */
    public void setMetadataOutputFormat(String metadataOutputFormat) {
        this.metadataOutputFormat = metadataOutputFormat;
    }

    /**
     * Gets the property setter to use when a metadata value is set.
     * @return property setter
         */
    public PropertySetter getOnSet() {
        return onSet;
    }
    /**
     * Sets the property setter to use when a metadata value is set.
     * @param onSet property setter
         */
    public void setOnSet(PropertySetter onSet) {
        this.onSet = onSet;
    }

    /**
     * Invoke the external application on a document.
     * @param doc document
     * @param input document content
     * @param output processed document output stream
     * @throws ImporterHandlerException failed to handle the document
     */
    public void handleDocument(
            HandlerDoc doc, InputStream input, OutputStream output)
            throws ImporterHandlerException {


        //TODO eliminate output an set it back on doc???

        validate();
        var cmd = command;
        final var files = new ArgFiles();
        var externalMeta = new Properties();

        //--- Resolve command tokens ---
        LOG.debug("Command before token replacement: {}", cmd);
        try {
            cmd = resolveInputToken(cmd, files, input);
            cmd = resolveInputMetaToken(cmd, files, input, doc.getMetadata());
            cmd = resolveOutputToken(cmd, files, output);
            cmd = resolveOutputMetaToken(cmd, files, output);
            cmd = resolveReferenceToken(cmd, doc.getReference());

            LOG.debug("Command after token replacement: {}", cmd);

            //--- Execute Command ---
            executeCommand(cmd, files, externalMeta, input, output);
            try {
                if (files.hasOutputFile() && output != null) {
                    FileUtils.copyFile(files.outputFile.toFile(), output);
                    output.flush();
                }
                if (files.hasOutputMetaFile()) {
                    try (Reader outputMetaReader = Files.newBufferedReader(
                            files.outputMetaFile)) {
                        var format = getMetadataOutputFormat();
                        if (META_FORMAT_PROPERTIES.equalsIgnoreCase(format)) {
                            externalMeta.loadFromProperties(outputMetaReader);
                        } else if (META_FORMAT_XML.equals(format)) {
                            externalMeta.loadFromXML(outputMetaReader);
                        } else if (META_FORMAT_JSON.equals(format)) {
                            externalMeta.loadFromJSON(outputMetaReader);
                        } else {
                            extractMetaFromFile(outputMetaReader, externalMeta);
                        }
                    }
                }
            } catch (IOException e) {
                throw new ImporterHandlerException(
                        "Could not read command output file. Command: "
                                + command, e);
            }
            // Set extracted metadata on actual metadata
            externalMeta.forEach((k, v) ->  PropertySetter
                    .orAppend(onSet)
                    .apply(doc.getMetadata(), k, v));
        } finally {
            files.deleteAll();
        }
    }

    private int executeCommand(
            final String cmd,
            final ArgFiles files,
            final Properties metadata,
            final InputStream input,
            final OutputStream output) throws ImporterHandlerException {
        var systemCommand = new SystemCommand(cmd);
        systemCommand.setEnvironmentVariables(environmentVariables);
        systemCommand.addOutputListener(new InputStreamLineListener() {
            @Override
            protected void lineStreamed(String type, String line) {
                if (!files.hasOutputFile() && output != null) {
                    writeLine(line, output);
                }
                if (!files.hasOutputMetaFile()) {
                    extractMetaFromLine(line, metadata);
                }
            }
        });
        systemCommand.addErrorListener(new InputStreamLineListener() {
            @Override
            protected void lineStreamed(String type, String line) {
                if (!files.hasOutputMetaFile()) {
                    extractMetaFromLine(line, metadata);
                }
            }
        });

        try {
            int exitValue;
            if (files.hasInputFile() || input == null) {
                exitValue = systemCommand.execute();
            } else {
                exitValue = systemCommand.execute(input);
            }
            if (exitValue != 0) {
                LOG.error("Bad command exit value: {}", exitValue);
            }
            return exitValue;
        } catch (SystemCommandException e) {
            throw new ImporterHandlerException(
                    "External transformer failed. Command: " + command, e);
        }
    }

    private void writeLine(String line, OutputStream output) {
        try {
            output.write(line.getBytes());
            output.write('\n');
            output.flush();
        } catch (IOException e) {
            throw new ImporterRuntimeException(
                    "Could not write to output", e);
        }
    }

    private synchronized void extractMetaFromFile(
            Reader reader, Properties metadata) {
        Iterator<String> it = IOUtils.lineIterator(reader);
        while (it.hasNext()) {
            extractMetaFromLine(it.next(), metadata);
        }
    }

    private synchronized void extractMetaFromLine(
            String line, Properties metadata) {
        RegexFieldValueExtractor.extractFieldValues(metadata, line,
                patterns.toArray(RegexFieldValueExtractor.EMPTY_ARRAY));
    }

    private Path createTempFile(
            Object stream, String name, String suffix)
                    throws ImporterHandlerException {
        Path tempDirectory;
        if (tempDir != null) {
            tempDirectory = tempDir;
        } else if (stream instanceof CachedStream cachedStream) {
            tempDirectory = cachedStream.getCacheDirectory();
        } else {
            tempDirectory = FileUtils.getTempDirectory().toPath();
        }
        Path file = null;
        try {
            if (!tempDirectory.toFile().exists()) {
                Files.createDirectories(tempDirectory);
            }
            return Files.createTempFile(tempDirectory, name, suffix);
        } catch (IOException e) {
            ArgFiles.delete(file);
            throw new ImporterHandlerException(
                    "Could not create temporary input file.", e);
        }
    }

    private String resolveInputToken(String cmd, ArgFiles files, InputStream is)
            throws ImporterHandlerException {
        if (!cmd.contains(TOKEN_INPUT) || is == null) {
            return cmd;
        }
        var newCmd = cmd;
        files.inputFile = createTempFile(is, "input", ".tmp");
        newCmd = StringUtils.replace(newCmd, TOKEN_INPUT,
                files.inputFile.toAbsolutePath().toString());
        try {
            FileUtils.copyInputStreamToFile(is, files.inputFile.toFile());
            return newCmd;
        } catch (IOException e) {
            ArgFiles.delete(files.inputFile);
            throw new ImporterHandlerException(
                    "Could not create temporary input file.", e);
        }
    }
    private String resolveInputMetaToken(
            String cmd, ArgFiles files, InputStream is, Properties meta)
                    throws ImporterHandlerException {
        if (!cmd.contains(TOKEN_INPUT_META)) {
            return cmd;
        }
        var newCmd = cmd;
        files.inputMetaFile = createTempFile(
                is, "input-meta", "." + StringUtils.defaultIfBlank(
                        getMetadataInputFormat(), META_FORMAT_JSON));
        newCmd = StringUtils.replace(newCmd, TOKEN_INPUT_META,
                files.inputMetaFile.toAbsolutePath().toString());
        try (Writer fw = Files.newBufferedWriter(files.inputMetaFile)) {
            var format = getMetadataInputFormat();
            if (META_FORMAT_PROPERTIES.equalsIgnoreCase(format)) {
                meta.storeToProperties(fw);
            } else if (META_FORMAT_XML.equals(format)) {
                meta.storeToXML(fw);
            } else {
                meta.storeToJSON(fw);
            }
            fw.flush();
            return newCmd;
        } catch (IOException e) {
            ArgFiles.delete(files.inputMetaFile);
            throw new ImporterHandlerException(
                    "Could not create temporary input metadata file.", e);
        }
    }

    private String resolveOutputToken(
            String cmd, ArgFiles files, OutputStream os)
            throws ImporterHandlerException {
        if (!cmd.contains(TOKEN_OUTPUT) || os == null) {
            return cmd;
        }
        var newCmd = cmd;
        files.outputFile = createTempFile(os, "output", ".tmp");
        return StringUtils.replace(newCmd, TOKEN_OUTPUT,
                files.outputFile.toAbsolutePath().toString());
    }

    private String resolveOutputMetaToken(
            String cmd, ArgFiles files, OutputStream os)
                    throws ImporterHandlerException {
        if (!cmd.contains(TOKEN_OUTPUT_META)) {
            return cmd;
        }
        var newCmd = cmd;
        files.outputMetaFile = createTempFile(
                os, "output-meta", "." + StringUtils.defaultIfBlank(
                        getMetadataOutputFormat(), ".tmp"));
        return StringUtils.replace(newCmd, TOKEN_OUTPUT_META,
                files.outputMetaFile.toAbsolutePath().toString());
    }

    private String resolveReferenceToken(String cmd, String reference) {
        if (!cmd.contains(TOKEN_REFERENCE)) {
            return cmd;
        }
        return StringUtils.replace(cmd, TOKEN_REFERENCE, reference);
    }

    private void validate() throws ImporterHandlerException {
        if (StringUtils.isBlank(command)) {
            throw new ImporterHandlerException("External command missing.");
        }
    }


    public void loadHandlerFromXML(XML xml) {

        setCommand(xml.getString("command", command));
        setTempDir(xml.getPath("tempDir", tempDir));
        setMetadataInputFormat(xml.getString(
                "metadata/@inputFormat", metadataInputFormat));
        setMetadataOutputFormat(xml.getString(
                "metadata/@outputFormat", metadataOutputFormat));
        setOnSet(xml.getEnum("metadata/@onSet", PropertySetter.class, onSet));

        var nodes = xml.getXMLList("metadata/pattern");
        for (XML node : nodes) {
            var ex = new RegexFieldValueExtractor();
            ex.loadFromXML(node);
            addMetadataExtractionPatterns(ex);
        }

        var xmlEnvs = xml.getXMLList("environment/variable");
        if (!xmlEnvs.isEmpty()) {
            Map<String, String> vars = new HashMap<>();
            for (XML node : xmlEnvs) {
                vars.put(node.getString("@name"), node.getString("."));
            }
            setEnvironmentVariables(vars);
        }
    }

    public void saveHandlerToXML(XML xml) {
        xml.addElement("command", command);
        xml.addElement("tempDir", tempDir);
        if (!getMetadataExtractionPatterns().isEmpty()) {
            var metaXML = xml.addElement("metadata")
                    .setAttribute("inputFormat", metadataInputFormat)
                    .setAttribute("outputFormat", metadataOutputFormat)
                    .setAttribute("onSet", onSet);

            for (RegexFieldValueExtractor rfe : patterns) {
                rfe.saveToXML(metaXML.addElement("pattern"));
            }
        }
        if (getEnvironmentVariables() != null) {
            var envXML = xml.addElement("environment");
            for (Entry<String, String> entry
                    : getEnvironmentVariables().entrySet()) {
                envXML.addElement("variable", entry.getValue())
                        .setAttribute("name", entry.getKey());
            }
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof ExternalHandler castOther)) {
            return false;
        }
        return EqualsBuilder.reflectionEquals(
                this, other, "environmentVariables") && EqualsUtil.equalsMap(
                        getEnvironmentVariables(),
                        castOther.getEnvironmentVariables());
    }
    @Override
    public int hashCode() {
        return HashCodeBuilder.reflectionHashCode(this);
    }
    @Override
    public String toString() {
        return new ReflectionToStringBuilder(
                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
    }

    static class ArgFiles {
        Path inputFile;
        Path inputMetaFile;
        Path outputFile;
        Path outputMetaFile;
        boolean hasInputFile() {
            return inputFile != null;
        }
        boolean hasInputMetaFile() {
            return inputMetaFile != null;
        }
        boolean hasOutputFile() {
            return outputFile != null;
        }
        boolean hasOutputMetaFile() {
            return outputMetaFile != null;
        }
        void deleteAll() {
            delete(inputFile);
            delete(inputMetaFile);
            delete(outputFile);
            delete(outputMetaFile);
        }
        static void delete(Path file) {
            if (file != null) {
                try {
                    java.nio.file.Files.delete(file);
                } catch (IOException e) {
                    LOG.warn("Could not delete temporary file: "
                            + file.toAbsolutePath(), e);
                }
            }
        }
    }
}
