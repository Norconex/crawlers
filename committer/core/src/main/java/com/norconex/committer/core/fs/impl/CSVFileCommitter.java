/* Copyright 2020-2022 Norconex Inc.
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

import static com.norconex.commons.lang.xml.XPathUtil.att;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVFormat.Builder;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.fs.AbstractFSCommitter;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.convert.EnumConverter;
import com.norconex.commons.lang.xml.XML;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
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
 * but you can supply your own name with {@link #setTypeHeader(String)}.
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
 * <h3>Content handling</h3>
 * <p>
 * The document content is represented by creating a column with a blank or
 * <code>null</code> field name. When requested, the "content" column
 * will always be present for both upserts and deletes, even if deletes do not
 * have content, for consistency.
 * </p>
 *
 * <h3>Truncate long values</h3>
 * <p>
 * By default, values longer than {@value #DEFAULT_TRUNCATE_AT} are truncated.
 * You can specify a different maximum length globally, or for each column.
 * Use <code>-1</code> for unlimited lenght, or <code>0</code> to use the
 * the global value, or {@value #DEFAULT_TRUNCATE_AT} if the global value
 * is also zero.
 * </p>
 *
 * <h3>CSV Format</h3>
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
 *
 * {@nx.xml.usage
 * <committer class="com.norconex.committer.core.fs.impl.CSVFileCommitter"
 *     format="(see class documentation)"
 *     showHeaders="[false|true]"
 *     delimiter="(single delimiter character)"
 *     quote="(single quote character)"
 *     escape="(single escape character)"
 *     multiValueJoinDelimiter="(delimiter string)"
 *     typeHeader="(header name for commit request type column)"
 *     truncateAt="(truncate after N characters, default: 5096, unlimited: -1)">
 *   <!-- Repeat "col" for every desired column. -->
 *   <col
 *       field="(source field name, omit or leave blank for document content)"
 *       header="(optional column header name)"
 *       truncateAt="(overwrite truncate)"/>
 *
 *   {@nx.include com.norconex.committer.core.fs.AbstractFSCommitter#options}
 *
 * </committer>
 * }
 *
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants
public class CSVFileCommitter extends AbstractFSCommitter<CSVPrinter> {

    public static final int DEFAULT_TRUNCATE_AT = 5096;

    private String format;
    private Character delimiter;
    private Character quote;
    private boolean showHeaders;
    private Character escape;
    private int truncateAt = DEFAULT_TRUNCATE_AT;
    private String multiValueJoinDelimiter;
    private String typeHeader;
    private final List<Column> columns = new ArrayList<>();

    public List<Column> getColumns() {
        return Collections.unmodifiableList(columns);
    }
    public void setColumns(List<Column> columns) {
        CollectionUtil.setAll(this.columns, columns);
    }

    @Override
    protected String getFileExtension() {
        return "csv";
    }
    @Override
    protected CSVPrinter createDocWriter(Writer writer) throws IOException {
        var builder = Builder.create(StringUtils.isBlank(format)
                ? CSVFormat.newFormat(',')
                : new EnumConverter().toType(
                        format, CSVFormat.Predefined.class).getFormat());

        if (delimiter != null) {
            builder.setDelimiter(delimiter);
        }
        if (quote != null) {
            builder.setQuote(quote);
        }
        if (escape != null) {
            builder.setEscape(escape);
        }

        builder.setRecordSeparator('\n');
        var csv = new CSVPrinter(writer, builder.build());
        printHeaders(csv);
        return csv;
    }

    private void printHeaders(CSVPrinter csv) throws IOException {
        if (!showHeaders) {
            return;
        }

        if (StringUtils.isNotBlank(typeHeader) || !isSplitUpsertDelete()) {
            csv.print(StringUtils.isNotBlank(typeHeader) ? typeHeader : "type");
        }
        for (Column column : columns) {
            var header = column.getHeader();
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

        if (StringUtils.isNotBlank(typeHeader) || !isSplitUpsertDelete()) {
            csv.print("upsert");
        }
        for (Column column : columns) {
            var field = column.getField();
            String value;
            // if blank field, we are dealing with content
            if (StringUtils.isBlank(field)) {
                value = IOUtils.toString(upsertRequest.getContent(), UTF_8);
            } else {
                value = StringUtils.join(
                        upsertRequest.getMetadata().getStrings(field),
                        multiValueJoinDelimiter);
            }
            value = truncate(value, column.getTruncateAt());
            csv.print(StringUtils.trimToEmpty(value));
        }
        csv.println();
    }

    @Override
    protected void writeDelete(
            CSVPrinter csv, DeleteRequest deleteRequest) throws IOException {

        if (StringUtils.isNotBlank(typeHeader) || !isSplitUpsertDelete()) {
            csv.print("delete");
        }
        for (Column column : columns) {
            var field = column.getField();
            // if blank field, we are dealing with content but delete has none,
            // so we store a blank value.
            var value = "";
            if (StringUtils.isNotBlank(field)) {
                value = StringUtils.join(
                        deleteRequest.getMetadata().getStrings(field),
                        multiValueJoinDelimiter);
            }
            value = truncate(value, column.getTruncateAt());
            csv.print(StringUtils.trimToEmpty(value));
        }
        csv.println();
    }

    private String truncate(String value, int colTruncateAt) {
        var max = colTruncateAt;
        // try global truncate
        if (max == 0) {
            max = getTruncateAt();
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
        if (csv!= null) {
            csv.flush();
            csv.close();
        }
    }

    @Override
    public void loadFSCommitterFromXML(XML xml) {
        setFormat(xml.getString(att(Fields.format), getFormat()));
        setDelimiter(xml.get(
                att(Fields.delimiter), Character.class, getDelimiter()));
        setQuote(xml.get(att(Fields.quote), Character.class, getQuote()));
        setShowHeaders(xml.getBoolean(
                att(Fields.showHeaders), isShowHeaders()));
        setEscape(xml.get(att(Fields.escape), Character.class, getEscape()));
        setTruncateAt(xml.getInteger(att(Fields.truncateAt), getTruncateAt()));
        setMultiValueJoinDelimiter(xml.getString(
                att(Fields.multiValueJoinDelimiter),
                getMultiValueJoinDelimiter()));
        setTypeHeader(xml.getString(att(Fields.typeHeader), getTypeHeader()));

        List<Column> cols = new ArrayList<>();
        for (XML colXml : xml.getXMLList("col")) {
            cols.add(new Column(
                    colXml.getString(att(Column.Fields.field), null),
                    colXml.getString(att(Column.Fields.header), null),
                    colXml.getInteger(att(Column.Fields.truncateAt), 0)));
        }
        setColumns(cols);
    }
    @Override
    public void saveFSCommitterToXML(XML xml) {
        xml.setAttribute(Fields.format, getFormat());
        xml.setAttribute(Fields.delimiter, getDelimiter());
        xml.setAttribute(Fields.quote, getQuote());
        xml.setAttribute(Fields.showHeaders, isShowHeaders());
        xml.setAttribute(Fields.escape, getEscape());
        xml.setAttribute(Fields.truncateAt, getTruncateAt());
        xml.setAttribute(Fields.multiValueJoinDelimiter,
                getMultiValueJoinDelimiter());
        xml.setAttribute(Fields.typeHeader, getTypeHeader());
        for (Column column : columns) {
            xml.addElement("col")
                    .setAttribute(Column.Fields.field, column.getField())
                    .setAttribute(Column.Fields.header, column.getHeader())
                    .setAttribute(Column.Fields.truncateAt,
                            column.getTruncateAt());
        }
    }

    @EqualsAndHashCode
    @ToString
    @FieldNameConstants
    public static class Column {
        private final String field;
        private final String header;
        private final int truncateAt; // -1 is unlimited, 0 is using default.

        public Column(String field) {
            this(field, field, 0);
        }
        public Column(String field, int truncateAt) {
            this(field, field, truncateAt);
        }
        public Column(String field, String header) {
            this(field, header, 0);
        }
        public Column(String field, String header, int truncateAt) {
            this.field = field;
            this.header = header;
            this.truncateAt = truncateAt;
        }
        public String getField() {
            return field;
        }
        public String getHeader() {
            return header;
        }
        public int getTruncateAt() {
            return truncateAt;
        }
    }
}
