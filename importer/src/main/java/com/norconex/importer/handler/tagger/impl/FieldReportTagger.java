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
package com.norconex.importer.handler.tagger.impl;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>A utility tagger that reports in a CSV file the fields discovered
 * in a crawl session, captured at the point of your choice in the
 * importing process.
 * If you use this class to report on all fields discovered, make sure you
 * use it as a post-parse handler, before you are limiting which fields
 * you want to keep.
 * </p>
 * <p>
 * The report will list one field per row, along with a few sample values
 * (3 by default).  The samples will be the first ones encountered.
 * </p>
 * <p>
 * This handler does not impact the data being imported at all
 * (it only reads it). It also do not store the "content" as a field.
 * </p>
 * <p>
 * When not specified with {@link #setFile(Path)}, a file called
 * "field-report.csv" will be created in the working directory.
 * </p>
 *
 * <p>Can be used both as a pre-parse or post-parse handler.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.tagger.impl.FieldReportTagger"
 *     maxSamples="(max number of sample values)"
 *     withHeaders="[false|true]"
 *     withOccurences="[false|true]"
 *     truncateSamplesAt="(number of characters to truncate long samples)"
 *     file="(path to a local file)" >
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="FieldReportTagger"
 *     maxSamples="1" file="C:\reports\field-report.csv" />
 * }
 * <p>
 * The above example logs all discovered fields into a "field-report.csv" file,
 * along with only 1 example value.
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
public class FieldReportTagger extends AbstractDocumentTagger {

    private static final Logger LOG =
            LoggerFactory.getLogger(FieldReportTagger.class);

    public static final int DEFAULT_MAX_SAMPLES = 3;
    public static final Path DEFAULT_FILE = Paths.get("./field-report.csv");

    private int maxSamples = DEFAULT_MAX_SAMPLES;
    private Path file;
    private boolean withHeaders;
    private boolean withOccurences;
    private int truncateSamplesAt = -1;

    private final Map<String, FieldData> fields = MapUtils.lazyMap(
            new TreeMap<String, FieldData>(), FieldData::new);

    public Path getFile() {
        return file;
    }
    public void setFile(Path file) {
        this.file = file;
    }

    public int getMaxSamples() {
        return maxSamples;
    }
    public void setMaxSamples(int maxSamples) {
        this.maxSamples = maxSamples;
    }

    public boolean isWithHeaders() {
        return withHeaders;
    }
    public void setWithHeaders(boolean withHeaders) {
        this.withHeaders = withHeaders;
    }

    public boolean isWithOccurences() {
        return withOccurences;
    }
    public void setWithOccurences(boolean withOccurences) {
        this.withOccurences = withOccurences;
    }

    public int getTruncateSamplesAt() {
        return truncateSamplesAt;
    }
    public void setTruncateSamplesAt(int truncateSamplesAt) {
        this.truncateSamplesAt = truncateSamplesAt;
    }

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {
        reportFields(doc.getMetadata());
    }

    private synchronized void reportFields(Properties metadata) {
        var dirty = false;
        for (Entry<String, List<String>> en : metadata.entrySet()) {
            if (reportField(en.getKey(), en.getValue())) {
                dirty = true;
            }
        }
        if (dirty) {
            saveReport();
        }
    }

    private boolean reportField(String field, List<String> samples) {
        var dirty = false;
        if (!fields.containsKey(field)) {
            dirty = true;
        }
        var fieldData = fields.get(field);
        if (fieldData.addSamples(samples, maxSamples, truncateSamplesAt)) {
            dirty = true;
        }
        return dirty;
    }

    private void saveReport() {
        var f = file == null ? DEFAULT_FILE : file;
        try (var printer = new CSVPrinter(
                new FileWriter(f.toFile()), CSVFormat.DEFAULT)) {
            if (withHeaders) {
                printer.print("Field Name");
                if (withOccurences) {
                    printer.print("Occurences");
                }
                for (var i = 1; i <= maxSamples; i++) {
                    printer.print("Sample Value " + i);
                }
                printer.println();
            }

            for (FieldData fieldData : fields.values()) {
                printer.print(fieldData.name);
                if (withOccurences) {
                    printer.print(fieldData.occurences);
                }
                for (String value : fieldData.values) {
                    printer.print(value);
                }
                // fill the blanks
                for (var i = 0; i < maxSamples - fieldData.values.size(); i++) {
                    printer.print("");
                }
                printer.println();
            }
            printer.flush();
        } catch (IOException e) {
            LOG.error("Could not write field report to: " + file, e);
        }
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setMaxSamples(xml.getInteger("@maxSamples", maxSamples));
        setFile(xml.getPath("@file", file));
        setWithHeaders(xml.getBoolean("@withHeaders", withHeaders));
        setWithOccurences(xml.getBoolean("@withOccurences", withOccurences));
        setTruncateSamplesAt(
                xml.getInteger("@truncateSamplesAt", truncateSamplesAt));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("maxSamples", maxSamples);
        xml.setAttribute("file", file);
        xml.setAttribute("withHeaders", withHeaders);
        xml.setAttribute("withOccurences", withOccurences);
        xml.setAttribute("truncateSamplesAt", truncateSamplesAt);
    }

    @Override
    public String toString() {
        var fieldSize = 0;
        synchronized (fields) {
            fieldSize = fields.size();
        }
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("file", file)
                .append("maxSamples", maxSamples)
                .append("withHeaders", withHeaders)
                .append("withOccurences", withOccurences)
                .append("truncateSamplesAt", truncateSamplesAt)
                .append("fields", "<size=" + fieldSize + ">")
                .toString();
    }

    @EqualsAndHashCode
    @ToString
    class FieldData {
        private final String name;
        private final Set<String> values = new HashSet<>();
        private int occurences;
        public FieldData(String name) {
            this.name = name;
        }
        // returns true if something changed
        public boolean addSamples(
                List<String> samples, int maxSamples, int truncateAt) {
            occurences++;
            if (samples == null) {
                return false;
            }
            var beforeCount = values.size();
            for (String sample : samples) {
                if ((values.size() >= maxSamples) || !StringUtils.isNotBlank(sample)) {
                    break;
                }
                if (truncateAt > -1) {
                    values.add(StringUtils.truncate(sample, truncateAt));
                } else {
                    values.add(sample);
                }
            }
            return withOccurences || beforeCount != values.size();
        }
    }
}
