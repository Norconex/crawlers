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
package com.norconex.importer.handler.splitter.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.splitter.AbstractDocumentSplitter;
import com.norconex.importer.parser.ParseState;
import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReaderBuilder;

import lombok.EqualsAndHashCode;
import lombok.ToString;

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
@EqualsAndHashCode
@ToString
public class CsvSplitter extends AbstractDocumentSplitter
        implements XMLConfigurable {

    public static final char DEFAULT_SEPARATOR_CHARACTER = ',';
    public static final char DEFAULT_QUOTE_CHARACTER = '"';
    public static final char DEFAULT_ESCAPE_CHARACTER = '\\';

    //--- Fields to define ---
    //TODO add to base class for most/all splitters with a protected method
    // that has the strategy
    // for creating references, default is to append to parent with !
    // and also setting common fields such as "parent-reference"

    //TODO add to base class the automated setting of parent elements to child
    // make optional to transfer all properties as is, or prefix them all,
    // except for document.parent.reference which should remain as is.

    private char separatorCharacter = DEFAULT_SEPARATOR_CHARACTER;
    private char quoteCharacter = DEFAULT_QUOTE_CHARACTER;
    private char escapeCharacter = DEFAULT_ESCAPE_CHARACTER;
    private boolean useFirstRowAsFields;
    private int linesToSkip;

    // These can be either column names or position, starting at 1
    private String referenceColumn;
    private final List<String> contentColumns = new ArrayList<>();

    @Override
    protected List<Doc> splitApplicableDocument(
            HandlerDoc doc, InputStream input,
            OutputStream output, ParseState parseState)
                    throws ImporterHandlerException {
        try {
            return doSplitApplicableDocument(doc, input);
        } catch (IOException e) {
            throw new ImporterHandlerException(
                    "Could not split document: " + doc.getReference(), e);
        }
    }

    private List<Doc> doSplitApplicableDocument(
            HandlerDoc doc, InputStream input) throws IOException {

        List<Doc> rows = new ArrayList<>();

        var parser = new CSVParserBuilder()
                .withSeparator(separatorCharacter)
                .withQuoteChar(quoteCharacter)
                .withEscapeChar(escapeCharacter)
                .build();

        //TODO by default (or as an option), try to detect the format of the
        // file (read first few lines and count number of tabs vs coma,
        // quotes per line, etc.
        try (var csvreader =
                new CSVReaderBuilder(
                        new InputStreamReader(input, StandardCharsets.UTF_8))
                .withSkipLines(linesToSkip)
                .withCSVParser(parser)
                .build()) {

            String [] cols;
            String[] colNames = null;
            var count = 0;
            var contentStr = new StringBuilder();
            while ((cols = csvreader.readNextSilently()) != null) {
                count++;
                var childMeta = new Properties();
                childMeta.loadFromMap(doc.getMetadata());
                var childEmbedRef = "row-" + count;
                if (count == 1 && useFirstRowAsFields) {
                    colNames = cols;
                } else {
                    for (var i = 0; i < cols.length; i++) {
                        var colPos = i + 1;
                        String colName = null;
                        if (colNames == null || i >= colNames.length) {
                            colName = "column" + colPos;
                        } else {
                            colName = colNames[i];
                        }
                        var colValue = cols[i];

                        // If a reference column, set reference value
                        if (isColumnMatching(colName, colPos,
                                Arrays.asList(referenceColumn))) {
                            childEmbedRef = colValue;
                        }
                        // If a content column, add it to content
                        if (isColumnMatching(colName, colPos, contentColumns)) {
                            if (contentStr.length() > 0) {
                                contentStr.append(" ");
                            }
                            contentStr.append(colValue);
                        }
                        childMeta.set(colName, colValue);
                    }
                    var childDocRef =
                            doc.getReference() + "!" + childEmbedRef;
                    CachedInputStream content = null;
                    if (contentStr.length() > 0) {
                        content = doc.getStreamFactory().newInputStream(
                                contentStr.toString());
                        contentStr.setLength(0);
                    } else {
                        content = doc.getStreamFactory().newInputStream();
                    }
                    var childDoc = new Doc(
                            childDocRef, content, childMeta);
                    var childInfo = childDoc.getDocInfo();
                    childInfo.setReference(childDocRef);
                    childInfo.addEmbeddedParentReference(doc.getReference());

                    childMeta.set(
                            DocMetadata.EMBEDDED_REFERENCE, childEmbedRef);

//                    childInfo.setEmbeddedReference(childEmbedRef);

//                    childMeta.setReference(childDocRef);
//                    childMeta.setEmbeddedReference(childEmbedRef);
//                    childMeta.setEmbeddedParentReference(doc.getReference());
//                    childMeta.setEmbeddedParentRootReference(doc.getReference());
                    rows.add(childDoc);
                }
            }
        }
        return rows;
    }

    private boolean isColumnMatching(
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


    /**
     * Gets the value-separator character.
     * @return value-separator character
     */
    public char getSeparatorCharacter() {
        return separatorCharacter;
    }
    /**
     * Sets the value-separator character. Default is a comma (,).
     * @param separatorCharacter value-separator character
     */
    public void setSeparatorCharacter(char separatorCharacter) {
        this.separatorCharacter = separatorCharacter;
    }

    /**
     * Get the value's surrounding quotes character.
     * @return value's surrounding quotes character
     */
    public char getQuoteCharacter() {
        return quoteCharacter;
    }
    /**
     * Sets the value's surrounding quotes character.  Default is the
     * double-quote character (").
     * @param quoteCharacter value's surrounding quotes character
     */
    public void setQuoteCharacter(char quoteCharacter) {
        this.quoteCharacter = quoteCharacter;
    }

    /**
     * Gets the escape character.
     * @return escape character
     */
    public char getEscapeCharacter() {
        return escapeCharacter;
    }
    /**
     * Sets the escape character.  Default is the backslash character (\).
     * @param escapeCharacter escape character
     */
    public void setEscapeCharacter(char escapeCharacter) {
        this.escapeCharacter = escapeCharacter;
    }

    /**
     * Whether to use the first row as field names for values.
     * @return <code>true</code> if using first row as field names.
     */
    public boolean isUseFirstRowAsFields() {
        return useFirstRowAsFields;
    }
    /**
     * Sets whether to use the first row as field names for values.
     * Default is <code>false</code>.
     * @param useFirstRowAsFields <code>true</code> if using first row as
     *        field names
     */
    public void setUseFirstRowAsFields(boolean useFirstRowAsFields) {
        this.useFirstRowAsFields = useFirstRowAsFields;
    }

    /**
     * Gets how many lines to skip before starting to parse lines.
     * @return how many lines to skip
     */
    public int getLinesToSkip() {
        return linesToSkip;
    }
    /**
     * Sets how many lines to skip before starting to parse lines.
     * Default is 0.
     * @param linesToSkip how many lines to skip
     */
    public void setLinesToSkip(int linesToSkip) {
        this.linesToSkip = linesToSkip;
    }

    public String getReferenceColumn() {
        return referenceColumn;
    }
    public void setReferenceColumn(String referenceColumn) {
        this.referenceColumn = referenceColumn;
    }

    public List<String> getContentColumns() {
        return Collections.unmodifiableList(contentColumns);
    }
    public void setContentColumns(String... contentColumns) {
        setContentColumns(Arrays.asList(contentColumns));
    }
    /**
     * Sets content columns.
     * @param contentColumns content columns
         */
    public void setContentColumns(List<String> contentColumns) {
        CollectionUtil.setAll(this.contentColumns, contentColumns);
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setSeparatorCharacter(loadCharacter(
                xml, "@separatorCharacter", separatorCharacter));
        setQuoteCharacter(loadCharacter(
                xml, "@quoteCharacter", quoteCharacter));
        setEscapeCharacter(loadCharacter(
                xml, "@escapeCharacter", escapeCharacter));
        setUseFirstRowAsFields(
                xml.getBoolean("@useFirstRowAsFields", useFirstRowAsFields));
        setLinesToSkip(xml.getInteger("@linesToSkip", linesToSkip));
        setReferenceColumn(
                xml.getString("@referenceColumn", referenceColumn));

        setContentColumns(
                xml.getDelimitedStringList("@contentColumns", contentColumns));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("separatorCharacter", separatorCharacter);
        xml.setAttribute("quoteCharacter", quoteCharacter);
        xml.setAttribute("escapeCharacter", escapeCharacter);
        xml.setAttribute("useFirstRowAsFields", useFirstRowAsFields);
        xml.setAttribute("linesToSkip", linesToSkip);
        xml.setAttribute("referenceColumn", referenceColumn);
        xml.setDelimitedAttributeList("contentColumns", contentColumns);
    }

    private char loadCharacter(
            XML xml, String key, char defaultCharacter) {
        var character = xml.getString(key, null);
        if (StringUtils.isEmpty(character)) {
            return defaultCharacter;
        }
        if (character.length() > 1) {
            throw new ConfigurationException(
                    "\"" + key + "\" value can only be a single character. "
                  + "Value: " + character);
        }
        return character.charAt(0);
    }
}
