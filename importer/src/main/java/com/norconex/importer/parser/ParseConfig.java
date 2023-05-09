/* Copyright 2023 Norconex Inc.
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
package com.norconex.importer.parser;

import static java.util.Optional.ofNullable;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.parser.impl.DefaultParser;
import com.norconex.importer.parser.impl.xfdl.XFDLParser;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.FieldNameConstants;

/**
 * <p>
 * Configuration for the parsing of documents. By default, most content types
 * are handled by the default parser, which relies on Apache Tika parsers.
 * You only have to overwrite/add some if need be.
 * </p>
 *
 * <h3>Limit parsing to specific content types</h3>
 * <p>
 * By default, all content types are parsed. You can limit which content
 * types get parsed with {@link #setContentTypeIncludes(List)} and
 * {@link #setContentTypeExcludes(List)}. When both includes and excludes
 * are specified and a content type is matched by both, excludes take
 * precedence.
 * Unparsed documents will be sent as is to the post handlers
 * and the calling application.  Use caution when using that feature since
 * post-parsing handlers (or applications) may expect text-only content for
 * them to execute properly.</b>
 * </p>
 *
 * <h3>Character encoding</h3>
 * <p>Parsing a document also attempts to detect the character encoding
 * (charset) of the extracted text to converts it to UTF-8. When ignoring
 * content-types, the character encoding conversion to UTF-8 does not
 * take place and your documents will likely retain their original encoding.
 * </p>
 *
 * <h3>Embedded documents</h3>
 * <p>
 * Refer to {@link EmbeddedConfig} for documentation on configuring the ways
 * you want embedded documents to be handled (or not).
 * </p>
 *
 * <h3>Optical character recognition (OCR):</h3>
 * <p>
 * OCR is performed using Tesseract. Refer to {@link OCRConfig} for
 * documentation on configuring OCR.
 * </p>
 *
 * {@nx.xml.usage
 * <parse>
 *
 *   <contentTypeIncludes>
 *     <!-- "matcher" is repeatable -->
 *     <matcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching one or more content types)
 *     </matcher>
 *   </contentTypeIncludes>
 *
 *   <contentTypeExcludes>
 *     <!-- "matcher" is repeatable -->
 *     <matcher {@nx.include com.norconex.commons.lang.text.TextMatcher#matchAttributes}>
 *       (expression matching one or more content types)
 *     </matcher>
 *   </contentTypeExcludes>
 *
 *   <errorsSaveDir>
 *     (optional path of directory where to save documents
 *      that failed to be parsed)
 *   </errorsSaveDir>
 *
 *   <defaultParser
 *       class="(optionally overwrite the default parser)" />
 *
 *   <parsers>
 *       <!-- Optionally set/overwrite the parser for specific content types.
 *            Repeatable. -->
 *       <parser
 *           contentType="(content type)"
 *           class="(DocumentParser implementing class)">
 *         (parser specific settings, if any)
 *       </parser>
 *   </parsers>
 *
 *   <parseOptions>
 *     {@nx.include com.norconex.importer.parser.OCRConfig@nx.xml.usage}
 *     {@nx.include com.norconex.importer.parser.EmbeddedConfig@nx.xml.usage}
 *
 *     <!-- Custom settings that may apply to one or several
 *          custom parsers when appropriate.
 *          Repeatable. -->
 *     <options>
 *       <option name="(option name)">(option value)</option>
 *     </options>
 *   <parseOptions>
 *
 * </parse>
 * }
 * @see EmbeddedConfig
 * @see OCRConfig
 */
@SuppressWarnings("javadoc")
@Data
@FieldNameConstants
public class ParseConfig implements XMLConfigurable {

    private final List<TextMatcher> contentTypeIncludes = new ArrayList<>();
    private final List<TextMatcher> contentTypeExcludes = new ArrayList<>();
    private Path errorsSaveDir;

    private DocumentParser defaultParser = new DefaultParser();
    private final Map<ContentType, DocumentParser> parsers = new HashMap<>();
    @NonNull
    private ParseOptions parseOptions = new ParseOptions();

    public ParseConfig() {
        parsers.put(
                ContentType.valueOf("application/vnd.xfdl"), new XFDLParser());
    }

    public void setParser(ContentType contentType, DocumentParser parser) {
        parsers.put(contentType, parser);
    }
    public void setParsers(Map<ContentType, DocumentParser> parsers) {
        CollectionUtil.setAll(this.parsers, parsers);
    }
    public Map<ContentType, DocumentParser> getParsers() {
        return Collections.unmodifiableMap(parsers);
    }

    public DocumentParser getParserOrDefault(ContentType contentType) {
        return ofNullable(parsers.get(contentType)).orElse(defaultParser);
    }

    public void setContentTypeIncludes(List<TextMatcher> contentTypeIncludes) {
        CollectionUtil.setAll(this.contentTypeIncludes, contentTypeIncludes);
    }
    public List<TextMatcher> getContentTypeIncludes() {
        return Collections.unmodifiableList(contentTypeIncludes);
    }
    public void setContentTypeExcludes(List<TextMatcher> contentTypeExcludes) {
        CollectionUtil.setAll(this.contentTypeExcludes, contentTypeExcludes);
    }
    public List<TextMatcher> getContentTypeExcludes() {
        return Collections.unmodifiableList(contentTypeExcludes);
    }

    @Override
    public void loadFromXML(XML xml) {
        var includeXMLs = xml.getXMLList("contentTypeIncludes/matcher");
        if (!includeXMLs.isEmpty()) {
            contentTypeIncludes.clear();
            for (XML includeXML : includeXMLs) {
                var matcher = new TextMatcher();
                matcher.loadFromXML(includeXML);
                contentTypeIncludes.add(matcher);
            }
        }
        var excludeXMLs = xml.getXMLList("contentTypeExcludes/matcher");
        if (!excludeXMLs.isEmpty()) {
            contentTypeExcludes.clear();
            for (XML excludeXML : excludeXMLs) {
                var matcher = new TextMatcher();
                matcher.loadFromXML(excludeXML);
                contentTypeExcludes.add(matcher);
            }
        }
        setErrorsSaveDir(xml.getPath(Fields.errorsSaveDir, getErrorsSaveDir()));
        setDefaultParser(xml.getObjectImpl(
                DocumentParser.class, Fields.defaultParser, defaultParser));
        // Parsers
        var nodes = xml.getXMLList("parsers/parser");
        for (XML node : nodes) {
            var parser = node.<DocumentParser>getObjectImpl(
                    DocumentParser.class, ".");
            var contentType = node.getString("@contentType");
            if (StringUtils.isBlank(contentType)) {
                throw new ConfigurationException(
                        "Attribute \"contentType\" missing for parser: "
                      + node.getString("@class"));
            }
            parsers.put(ContentType.valueOf(contentType), parser);
        }
        parseOptions.loadFromXML(xml.getXML("parseOptions"));
    }
    @Override
    public void saveToXML(XML xml) {
        xml.addElementList(
                Fields.contentTypeIncludes, "matcher", contentTypeIncludes);
        xml.addElementList(
                Fields.contentTypeExcludes, "matcher", contentTypeExcludes);
        xml.addElement(Fields.errorsSaveDir, errorsSaveDir);
        xml.addElement(Fields.defaultParser, defaultParser);
        if (!parsers.isEmpty()) {
            var parsersXML = xml.addElement("parsers");

            for (Entry<ContentType, DocumentParser> entry:
                    parsers.entrySet()) {
                parsersXML.addElement("parser", entry.getValue())
                        .setAttribute("contentType", entry.getKey().toString());
            }
        }
        parseOptions.saveToXML(xml.addElement("parseOptions"));
    }
}
