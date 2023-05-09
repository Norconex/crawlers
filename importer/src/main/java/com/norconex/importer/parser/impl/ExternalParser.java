/* Copyright 2015-2022 Norconex Inc.
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
package com.norconex.importer.parser.impl;

import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.output.WriterOutputStream;

import com.norconex.commons.lang.map.PropertySetter;
import com.norconex.commons.lang.text.RegexFieldValueExtractor;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.handler.ExternalHandler;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.transformer.impl.ExternalTransformer;
import com.norconex.importer.parser.DocumentParser;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.ParseOptions;

import lombok.NonNull;

/**
 * <p>
 * Parses and extracts text from a file using an external application to do so.
 * </p>
 * <p>
 * This class relies on {@link ExternalHandler} for most of the work.
 * Refer to {@link ExternalHandler} for full documentation.
 * </p>
 * <p>
 * This parser can be made configurable via XML. See
 * {@link GenericDocumentParserFactory} for general indications how
 * to configure parsers.
 * </p>
 * <p>
 * To use an external application to change a file content after parsing has
 * already occurred, consider using {@link ExternalTransformer} instead.
 * </p>
 *
 * {@nx.xml.usage
 * <parser contentType="(content type this parser is associated to)"
 *     class="com.norconex.importer.parser.impl.ExternalParser" >
 *
 *   <command>
 *     c:\Apps\myapp.exe ${INPUT} ${OUTPUT} ${INPUT_META} ${OUTPUT_META} ${REFERENCE}
 *   </command>
 *
 *   <metadata
 *       inputFormat="[json|xml|properties]"
 *       outputFormat="[json|xml|properties]"
 *       {@nx.include com.norconex.commons.lang.map.PropertySetter#attributes}>
 *     <!-- pattern only used when no output format is specified -->
 *     <pattern {@nx.include com.norconex.commons.lang.text.RegexFieldValueExtractor#attributes}>
 *       (regular expression)
 *     </pattern>
 *     <!-- repeat pattern tag as needed -->
 *   </metadata>
 *
 *   <environment>
 *     <variable name="(environment variable name)">
 *       (environment variable value)
 *     </variable>
 *     <!-- repeat variable tag as needed -->
 *   </environment>
 *
 * </parser>
 * }
 *
 *
 * {@nx.xml.example
 * <parser contentType="text/plain"
 *     class="com.norconex.importer.parser.impl.ExternalParser" >
 *   <command>/path/transform/app ${INPUT} ${OUTPUT}</command>
 *   <metadata>
 *     <pattern field="docnumber" valueGroup="1">DocNo:(\d+)</pattern>
 *   </metadata>
 * </parser>
 * }
 *
 * <p>
 * The above example invokes an external application processing for
 * simple text files that accepts two files as arguments:
 * the first one being the file to
 * transform, the second one being holding the transformation result.
 * It also extract a document number from STDOUT, found as "DocNo:1234"
 * and storing it as "docnumber".
 * </p>
 *
 * @see ExternalHandler
 */
@SuppressWarnings("javadoc")
public class ExternalParser implements DocumentParser, XMLConfigurable {

    private final ExternalHandler h = new ExternalHandler();

    /**
     * Gets the command to execute.
     * @return the command
     */
    public String getCommand() {
        return h.getCommand();
    }
    /**
     * Sets the command to execute. Make sure to escape spaces in
     * executable path and its arguments as well as other special command
     * line characters.
     * @param command the command
     */
    public void setCommand(String command) {
        h.setCommand(command);
    }

    /**
     * Gets directory where to store temporary files used for transformation.
     * @return temporary directory
         */
    public Path getTempDir() {
        return h.getTempDir();
    }
    /**
     * Sets directory where to store temporary files used for transformation.
     * @param tempDir temporary directory
         */
    public void setTempDir(Path tempDir) {
        h.setTempDir(tempDir);
    }

    /**
     * Gets metadata extraction patterns. See class documentation.
     * @return map of patterns and field names
     */
    public List<RegexFieldValueExtractor> getMetadataExtractionPatterns() {
        return h.getMetadataExtractionPatterns();
    }

    /**
     * Adds a metadata extraction pattern that will extract the whole text
     * matched into the given field.
     * @param field target field to store the matching pattern.
     * @param pattern the pattern
         */
    public void addMetadataExtractionPattern(String field, String pattern) {
        h.addMetadataExtractionPattern(field, pattern);
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
        h.addMetadataExtractionPattern(field, pattern, valueGroup);
    }
    /**
     * Adds a metadata extraction pattern that will extract matching field
     * names/values.
     * @param patterns extraction pattern
         */
    public void addMetadataExtractionPatterns(RegexFieldValueExtractor... patterns) {
        h.addMetadataExtractionPatterns(patterns);
    }
    /**
     * Sets metadata extraction patterns. Clears any previously assigned
     * patterns.
     * @param patterns extraction pattern
         */
    public void setMetadataExtractionPatterns(RegexFieldValueExtractor... patterns) {
        h.setMetadataExtractionPatterns(patterns);
    }

    /**
     * Gets the property setter to use when a metadata value is set.
     * @return property setter
         */
    public PropertySetter getOnSet() {
        return h.getOnSet();
    }
    /**
     * Sets the property setter to use when a metadata value is set.
     * @param onSet property setter
         */
    public void setOnSet(PropertySetter onSet) {
        h.setOnSet(onSet);
    }

    /**
     * Gets environment variables.
     * @return environment variables or <code>null</code> if using the current
     *         process environment variables
     */
    public Map<String, String> getEnvironmentVariables() {
        return h.getEnvironmentVariables();
    }
    /**
     * Sets the environment variables. Clearing any prevously assigned
     * environment variables. Set <code>null</code> to use
     * the current process environment variables (default).
     * @param environmentVariables environment variables
     */
    public void setEnvironmentVariables(
            Map<String, String> environmentVariables) {
        h.setEnvironmentVariables(environmentVariables);
    }
    /**
     * Adds the environment variables, keeping environment variables previously
     * assigned. Existing variables of the same name
     * will be overwritten. To clear all previously assigned variables and use
     * the current process environment variables, pass
     * <code>null</code> to
     * {@link ExternalParser#setEnvironmentVariables(Map)}.
     * @param environmentVariables environment variables
     */
    public void addEnvironmentVariables(
            Map<String, String> environmentVariables) {
        h.addEnvironmentVariables(environmentVariables);
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
        h.addEnvironmentVariable(name, value);
    }

    /**
     * Gets the format of the metadata input file sent to the external
     * application. One of "json" (default), "xml", or "properties" is expected.
     * Only applicable when the <code>${INPUT}</code> token
     * is part of the command.
     * @return metadata input format
         */
    public String getMetadataInputFormat() {
        return h.getMetadataInputFormat();
    }
    /**
     * Sets the format of the metadata input file sent to the external
     * application. One of "json" (default), "xml", or "properties" is expected.
     * Only applicable when the <code>${INPUT}</code> token
     * is part of the command.
     * @param metadataInputFormat format of the metadata input file
         */
    public void setMetadataInputFormat(String metadataInputFormat) {
        h.setMetadataInputFormat(metadataInputFormat);
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
        return h.getMetadataOutputFormat();
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
        h.setMetadataOutputFormat(metadataOutputFormat);
    }

    @Override
    public void init(@NonNull ParseOptions parseOptions)
            throws DocumentParserException {
        //NOOP
    }

    @Override
    public List<Doc> parseDocument(Doc doc,
            Writer output) throws DocumentParserException {
        try {
            h.handleDocument(new HandlerDoc(doc), doc.getInputStream(),
                    new WriterOutputStream(output, StandardCharsets.UTF_8));
        } catch (ImporterHandlerException e) {
            throw new DocumentParserException(
                    "Could not parse document: " + doc.getReference(), e);
        }
        return Collections.emptyList();
    }

    @Override
    public void loadFromXML(XML xml) {
        h.loadHandlerFromXML(xml);
    }

    @Override
    public void saveToXML(XML xml) {
        h.saveHandlerToXML(xml);
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof ExternalParser castOther)) {
            return false;
        }
        return h.equals(castOther.h);
    }
    @Override
    public int hashCode() {
        return h.hashCode();
    }
    @Override
    public String toString() {
        var toString = h.toString();
        return toString.replaceFirst(
            "ExternalTransformer\\[restrictions=\\[.*?\\],",
            ExternalParser.class.getSimpleName() + "[");
    }
}
