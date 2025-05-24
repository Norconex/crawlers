/* Copyright 2020-2025 Norconex Inc.
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
package com.norconex.committer.core.fs.impl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.io.IOException;
import java.io.Writer;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVFormat.Builder;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.fs.AbstractFsCommitter;
import com.norconex.commons.lang.convert.EnumConverter;

import lombok.Data;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * Commits documents to CSV files (Comma Separated Value).
 * There are two kinds of document representations: upserts and deletions.
 * </p>
 * <p>
 * If you request to split upserts and deletions into separate files,
 * the generated files will start with "upsert-" (for additions/modifications)
 * and "delete-" (for deletions).
 * A request "type" field is always added when both upserts and deletes are
 * added to the same file.  Default header name for it is <code>type</code>,
 * but you can supply your own name with
 * {@link CsvFileCommitterConfig#setTypeHeader(String)}.
 * </p>
 * <p>
 * The generated files are never updated.  Sending a modified document with the
 * same reference will create a new entry and won't modify any existing ones.
 * You can think of the generated files as a set of commit instructions.
 * </p>
 * <p>
 * The generated CSV file names are made of a timestamp and a sequence number.
 * </p>
 * <p>
 * You have the option to give a prefix or suffix to
 * files that will be created (default does not add any).
 * </p>
 *
 * <h2>Content handling</h2>
 * <p>
 * The document content is represented by creating a column with a blank or
 * {@code null} field name. When requested, the "content" column
 * will always be present for both upserts and deletes, even if deletes do not
 * have content, for consistency.
 * </p>
 *
 * <h2>Truncate long values</h2>
 * <p>
 * By default, values longer than {@value #DEFAULT_TRUNCATE_AT} are truncated.
 * You can specify a different maximum length globally, or for each column.
 * Use <code>-1</code> for unlimited lenght, or <code>0</code> to use the
 * the global value, or {@value #DEFAULT_TRUNCATE_AT} if the global value
 * is also zero.
 * </p>
 *
 * <h2>CSV Format</h2>
 * <p>
 * Applications consuming CSV files often have different expectations.
 * Subtle format differences that can make opening or parsing a generated
 * CSV file difficult. To help with this, there are preset CSV formats
 * you can chose from:
 * </p>
 * <ul>
 *   <li>DEFAULT</li>
 *   <li>EXCEL</li>
 *   <li>INFORMIX_UNLOAD1.3</li>
 *   <li>INFORMIX_UNLOAD_CSV1.3</li>
 *   <li>MONGO_CSV1.7</li>
 *   <li>MONGO_TSV1.7</li>
 *   <li>MYSQL</li>
 *   <li>ORACLE1.6</li>
 *   <li>POSTGRESSQL_CSV1.5</li>
 *   <li>POSTGRESSQL_TEXT1.5</li>
 *   <li>RFC-4180</li>
 *   <li>TDF</li>
 * </ul>
 *
 * <p>
 * More information on those can be obtained on
 * <a href="https://commons.apache.org/proper/commons-csv/user-guide.html">
 * Apache Commons CSV</a> website.
 * Other formatting options you explicitely configure will overwrite
 * the corresponding setting for the chosen format.
 * </p>
 */
@Data
@FieldNameConstants
public class CsvFileCommitter
        extends AbstractFsCommitter<CSVPrinter, CsvFileCommitterConfig> {

    public static final int DEFAULT_TRUNCATE_AT = 5096;

    private final CsvFileCommitterConfig configuration =
            new CsvFileCommitterConfig();

    @Override
    protected String getFileExtension() {
        return "csv";
    }

    @Override
    protected CSVPrinter createDocWriter(Writer writer) throws IOException {
        var builder = Builder.create(
                configuration.getFormat() == null
                        ? CSVFormat.newFormat(',')
                        : new EnumConverter().toType(
                                configuration.getFormat().toString(),
                                CSVFormat.Predefined.class).getFormat());

        if (configuration.getDelimiter() != null) {
            builder.setDelimiter(configuration.getDelimiter());
        }
        if (configuration.getQuote() != null) {
            builder.setQuote(configuration.getQuote());
        }
        if (configuration.getEscape() != null) {
            builder.setEscape(configuration.getEscape());
        }

        builder.setRecordSeparator('\n');
        var csv = new CSVPrinter(writer, builder.build());
        printHeaders(csv);
        return csv;
    }

    private void printHeaders(CSVPrinter csv) throws IOException {
        if (!configuration.isShowHeaders()) {
            return;
        }

        if (StringUtils.isNotBlank(configuration.getTypeHeader())
                || !configuration.isSplitUpsertDelete()) {
            csv.print(
                    isNotBlank(configuration.getTypeHeader())
                            ? configuration.getTypeHeader()
                            : "type");
        }
        for (CsvColumn col : configuration.getColumns()) {
            var header = col.getHeader();
            if (StringUtils.isBlank(header)) {
                header = "content";
            }
            csv.print(header);
        }
        csv.println();
    }

    @Override
    protected void writeUpsert(
            CSVPrinter csv, UpsertRequest upsertRequest) throws IOException {

        if (isNotBlank(configuration.getTypeHeader())
                || !configuration.isSplitUpsertDelete()) {
            csv.print("upsert");
        }
        for (CsvColumn col : configuration.getColumns()) {
            var field = col.getField();
            String value;
            // if blank field, we are dealing with content
            if (StringUtils.isBlank(field)) {
                value = IOUtils.toString(upsertRequest.getContent(), UTF_8);
            } else {
                value = StringUtils.join(
                        upsertRequest.getMetadata().getStrings(field),
                        configuration.getMultiValueJoinDelimiter());
            }
            value = truncate(value, col.getTruncateAt());
            csv.print(StringUtils.trimToEmpty(value));
        }
        csv.println();
        csv.flush();
    }

    @Override
    protected void writeDelete(
            CSVPrinter csv, DeleteRequest deleteRequest) throws IOException {

        if (isNotBlank(configuration.getTypeHeader())
                || !configuration.isSplitUpsertDelete()) {
            csv.print("delete");
        }
        for (CsvColumn col : configuration.getColumns()) {
            var field = col.getField();
            // if blank field, we are dealing with content but delete has none,
            // so we store a blank value.
            var value = "";
            if (StringUtils.isNotBlank(field)) {
                value = StringUtils.join(
                        deleteRequest.getMetadata().getStrings(field),
                        configuration.getMultiValueJoinDelimiter());
            }
            value = truncate(value, col.getTruncateAt());
            csv.print(StringUtils.trimToEmpty(value));
        }
        csv.println();
        csv.flush();
    }

    private String truncate(String value, int colTruncateAt) {
        var max = colTruncateAt;
        // try global truncate
        if (max == 0) {
            max = configuration.getTruncateAt();
        }
        // if still zero, use default
        if (max == 0) {
            max = DEFAULT_TRUNCATE_AT;
        }

        if (max < 0) {
            return value;
        }
        return StringUtils.truncate(value, max);
    }

    @Override
    protected void closeDocWriter(CSVPrinter csv)
            throws IOException {
        if (csv != null) {
            csv.flush();
            csv.close();
        }
    }
}
