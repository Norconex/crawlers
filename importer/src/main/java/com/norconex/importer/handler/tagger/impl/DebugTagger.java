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
package com.norconex.importer.handler.tagger.impl;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.norconex.commons.lang.SLF4JUtil;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.tagger.AbstractDocumentTagger;
import com.norconex.importer.parser.ParseState;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * <p>A utility tagger to help with troubleshooting of document importing.
 * Place this tagger anywhere in your handler configuration to print to
 * the log stream the metadata fields or content so far when this handler
 * gets invoked.
 * This handler does not impact the data being imported at all
 * (it only reads it).</p>
 *
 * <p>The default behavior logs all metadata fields using the DEBUG log level.
 * You can optionally set which fields to log and whether to also log the
 * document content or not, as well as specifying a different log level.</p>
 *
 * <p><b>Be careful:</b> Logging the content when you deal with very large
 * content can result in memory exceptions.</p>
 *
 * <p>Can be used both as a pre-parse or post-parse handler.</p>
 *
 * {@nx.xml.usage
 *  <handler class="com.norconex.importer.handler.tagger.impl.DebugTagger"
 *          logFields="(CSV list of fields to log)"
 *          logContent="[false|true]"
 *          logLevel="[ERROR|WARN|INFO|DEBUG|TRACE]"
 *          prefix="(optional log prefix to further help you locate it)" >
 *
 *   {@nx.include com.norconex.importer.handler.AbstractImporterHandler#restrictTo}
 *
 *  </handler>
 * }
 * {@nx.xml.example
 *  <handler class="DebugTagger" logFields="title,author" logLevel="INFO" />
 * }
 * <p>
 * The above logs the value of any "title" and "author" document metadata
 * fields.
 * </p>
 */
@SuppressWarnings("javadoc")
@EqualsAndHashCode
@ToString
public class DebugTagger extends AbstractDocumentTagger {

    private static final Logger LOG =
            LoggerFactory.getLogger(DebugTagger.class);

    private final List<String> logFields = new ArrayList<>();
    private boolean logContent;
    private String logLevel;
    private String prefix;

    @Override
    public void tagApplicableDocument(
            HandlerDoc doc, InputStream document, ParseState parseState)
                    throws ImporterHandlerException {

        var level = Level.valueOf(
                ObjectUtils.defaultIfNull(logLevel, "debug").toUpperCase());

        if (logFields.isEmpty()) {
            for (Entry<String, List<String>> entry :
                    doc.getMetadata().entrySet()) {
                logField(level, entry.getKey(), entry.getValue());
            }
        } else {
            for (String fieldName : logFields) {
                logField(level, fieldName, doc.getMetadata().get(fieldName));
            }
        }

        if (logContent) {
            try {
                SLF4JUtil.log(LOG, level,
                        StringUtils.trimToEmpty(prefix) + "CONTENT={}",
                        IOUtils.toString(document, StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new ImporterHandlerException(
                        "Count not stream content.", e);
            }
        }
    }

    private void logField(Level level, String fieldName, List<String> values) {
        var b = new StringBuilder();
        if (values == null) {
            b.append("<null>");
        } else {
            for (String value : values) {
                if (b.length() > 0) {
                    b.append(", ");
                }
                b.append(value);
            }
        }
        SLF4JUtil.log(LOG, level, StringUtils.trimToEmpty(prefix)
                + "{}={}", fieldName, b.toString());
    }

    public List<String> getLogFields() {
        return Collections.unmodifiableList(logFields);
    }
    public void setLogFields(List<String> logFields) {
        CollectionUtil.setAll(this.logFields, logFields);
    }

    public boolean isLogContent() {
        return logContent;
    }
    public void setLogContent(boolean logContent) {
        this.logContent = logContent;
    }

    public String getLogLevel() {
        return logLevel;
    }
    public void setLogLevel(String logLevel) {
        this.logLevel = logLevel;
    }

    /**
     * Gets the prefix to print before the actual log message.
     * @return log entry prefix
     */
    public String getPrefix() {
        return prefix;
    }
    /**
     * Sets the prefix to print before the actual log message.
     * @param prefix log entry prefix
     */
    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected void loadHandlerFromXML(XML xml) {
        setLogContent(xml.getBoolean("@logContent", logContent));
        setLogFields(xml.getDelimitedStringList("@logFields", logFields));
        setLogLevel(xml.getString("@logLevel", logLevel));
        setPrefix(xml.getString("@prefix", prefix));
    }

    @Override
    protected void saveHandlerToXML(XML xml) {
        xml.setAttribute("logContent", logContent);
        xml.setDelimitedAttributeList("logFields", logFields);
        xml.setAttribute("logLevel", logLevel);
        xml.setAttribute("prefix", prefix);
    }
}
