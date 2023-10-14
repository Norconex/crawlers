/* Copyright 2014-2023 Norconex Inc.
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
package com.norconex.importer.handler.splitter.impl;

import static org.apache.commons.lang3.StringUtils.isBlank;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.splitter.DocumentSplitter;
import com.norconex.importer.parser.ParseState;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;

import lombok.Data;

/**
 * <p>Split files with Coma-Separated values (or any other characters, like tab)
 * into one document per line.</p>
 *
 * <p>Can be used both as a pre-parse (text documents) or post-parse handler
 * documents.</p>
 *
 * {@nx.xml.usage
 * <handler class="com.norconex.importer.handler.splitter.impl.CsvSplitter"
 *          separatorCharacter=""
 *          quoteCharacter=""
 *          escapeCharacter=""
 *          useFirstRowAsFields="(false|true)"
 *          linesToSkip="(integer)"
 *          referenceColumn="(column name or position from 1)"
 *          contentColumns="(csv list of column/position to use as content)" >
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 * </handler>
 * }
 *
 * {@nx.xml.example
 * <handler class="CsvSplitter"
 *     separatorCharacter=","
 *     quoteCharacter="'"
 *     escapeCharacter="\"
 *     useFirstRowAsFields="true"
 *     linesToSkip="0"
 *     referenceColumn="clientId"
 *     contentColumns="orgDesc" />
 * }
 * <p>
 * Given this sample CSV file content...
 * </p>
 * <pre>
 * 'clientId','clientName','clientOrg','orgDesc'
 * '123','Joe Dalton','ACME Inc.','Organization\'s description'
 * '345','Avrel Dalton','Daisy Town','Another one'
 * </pre>
 * <p>
 * ... the above example will split the file into two documents (one for each
 * row after the header row):
 * </p>
 *
 */
@SuppressWarnings("javadoc")
@Data
public class CsvSplitter
        implements DocumentSplitter, Configurable<CsvSplitterConfig> {

    private final CsvSplitterConfig configuration = new CsvSplitterConfig();

    @Override
    public List<Doc> splitDocument(HandlerDoc doc, InputStream docInput,
            OutputStream docOutput, ParseState parseState)
                    throws ImporterHandlerException {
        try {
            return doSplitApplicableDocument(doc, docInput);
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Could not split document: " + doc.getReference(), e);
        }
    }

    private List<Doc> doSplitApplicableDocument(
            HandlerDoc doc, InputStream input) throws IOException {

        List<Doc> rows = new ArrayList<>();

        var parser = new CSVParserBuilder()
                .withSeparator(configuration.getSeparatorCharacter())
                .withQuoteChar(configuration.getQuoteCharacter())
                .withEscapeChar(configuration.getEscapeCharacter())
                .build();

        //TODO by default (or as an option), try to detect the format of the
        // file (read first few lines and count number of tabs vs coma,
        // quotes per line, etc.
        try (var csvreader =
                new CSVReaderBuilder(
                        new InputStreamReader(input, StandardCharsets.UTF_8))
                .withSkipLines(configuration.getLinesToSkip())
                .withCSVParser(parser)
                .build()) {

            String [] rowColumns;
            String[] colNames = null;
            var count = 0;
            while ((rowColumns = csvreader.readNextSilently()) != null) {
                count++;
                var childEmbedRef = "row-" + count;
                if (count == 1 && configuration.isUseFirstRowAsFields()) {
                    colNames = rowColumns;
                } else {
                    rows.add(parseRow(
                            doc, rowColumns, colNames, childEmbedRef));
                }
            }
        }
        return rows;
    }

    private Doc parseRow(HandlerDoc doc, String[] rowColumns,
            String[] colNames,
            String childEmbedRef) {
        var contentStr = new StringBuilder();
        var childMeta = new Properties();
        childMeta.loadFromMap(doc.getMetadata());

        for (var i = 0; i < rowColumns.length; i++) {
            var colPos = i + 1;
            String colName = null;
            if (colNames == null || i >= colNames.length) {
                colName = "column" + colPos;
            } else {
                colName = colNames[i];
            }
            var colValue = rowColumns[i];

            // If a reference column, set reference value
            var refColumn = configuration.getReferenceColumn();
            if (isColumnMatchingNameOrPosition(colName, colPos,
                    isBlank(refColumn) ? List.of() : List.of(refColumn))) {
                childEmbedRef = colValue;
            }
            // If a content column, add it to content
            if (isColumnMatchingNameOrPosition(
                    colName, colPos, configuration.getContentColumns())) {
                if (contentStr.length() > 0) {
                    contentStr.append(" ");
                }
                contentStr.append(colValue);
            }
            childMeta.set(colName, colValue);
        }
        var childDocRef = doc.getReference() + "!" + childEmbedRef;
        CachedInputStream content = null;
        if (contentStr.length() > 0) {
            content = doc.getStreamFactory().newInputStream(
                    contentStr.toString());
        } else {
            content = doc.getStreamFactory().newInputStream();
        }
        var childDoc = new Doc(childDocRef, content, childMeta);
        var childInfo = childDoc.getDocRecord();
        childInfo.setReference(childDocRef);
        childInfo.addEmbeddedParentReference(doc.getReference());

        childMeta.set(DocMetadata.EMBEDDED_REFERENCE, childEmbedRef);

        return childDoc;
    }

    private boolean isColumnMatchingNameOrPosition(
            String colName, int colPosition, List<String> namesOrPossToMatch) {
        if (CollectionUtils.isEmpty(namesOrPossToMatch)) {
            return false;
        }
        for (String nameOrPosToMatch : namesOrPossToMatch) {
            if (StringUtils.isBlank(nameOrPosToMatch)) {
                continue;
            }
            if (Objects.equals(nameOrPosToMatch, colName)
                   || NumberUtils.toInt(nameOrPosToMatch) == colPosition) {
                return true;
            }
        }
        return false;
    }
}
