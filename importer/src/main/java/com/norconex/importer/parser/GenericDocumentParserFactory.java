/* Copyright 2010-2022 Norconex Inc.
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

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.tika.parser.jdbc.SQLite3Parser;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.config.ConfigurationException;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.importer.parser.impl.FallbackParser;
import com.norconex.importer.parser.impl.xfdl.XFDLParser;
import com.norconex.importer.response.ImporterResponse;

/**
 * <p>Generic document parser factory.  It uses Apacke Tika for <i>most</i> of
 * its supported content types.  For unknown
 * content types, it falls back to Tika generic media detector/parser.</p>
 *
 * <p>As of 2.6.0, it is possible to register your own parsers.</p>
 *
 * <h3>Ignoring content types:</h3>
 * <p>You can "ignore" content-types so they do not get
 * parsed. Unparsed documents will be sent as is to the post handlers
 * and the calling application.   Use caution when using that feature since
 * post-parsing handlers (or applications) usually expect text-only content for
 * them to execute properly.  Unless you really know what you are doing, <b>
 * avoid excluding binary content types from parsing.</b></p>
 *
 * <h3>Character encoding:</h3>
 * <p>Parsing a document also attempts to detect the character encoding
 * (charset) of the extracted text to converts it to UTF-8. When ignoring
 * content-types, the character encoding conversion to UTF-8 cannot
 * take place and your documents will likely retain their original encoding.
 * </p>
 *
 * <h3>Embedded documents:</h3>
 * <p>For documents containing embedded documents (e.g. zip files), the default
 * behavior of this treat them as a single document, merging all
 * embedded documents content and metadata into the parent document.
 * You can tell this parser to "split" embedded
 * documents to have them treated as if they were individual documents.  When
 * split, each embedded documents will go through the entire import cycle,
 * going through your handlers and even this parser again
 * (just like any regular document would).  The resulting
 * {@link ImporterResponse} should then contain nested documents, which in turn,
 * might contain some (tree-like structure). As of 2.6.0, this is enabled by
 * specifying a regular expression to match content types of container
 * documents you want to "split".
 * </p>
 *
 * <p>In addition, since 2.6.0 you can control which embedded documents you
 * do not want extracted from their containers, as well as which documents
 * containers you do not want to extract their embedded documents.
 * </p>
 *
 * <h3>Optical character recognition (OCR):</h3>
 * <p>You can configure this parser to use the
 * <b><a href="https://code.google.com/p/tesseract-ocr/">Tesseract</a></b>
 * open-source OCR application to extract text out of images
 * or documents containing embedded images (e.g. PDF).  Supported image
 * formats are TIFF, PNG, JPEG, GIF, and BMP.</p>
 *
 * <p>To enable this feature, you must
 * first download and install a copy of Tesseract appropriate for
 * your platform (supported are Linux, Windows, Mac and other platforms).
 * It will only be activated once you configure the path to its install
 * location.
 * Default language detection is for English. To support additional or
 * different languages,
 * you can provide a list of three-letter ISO language codes supported
 * by Tesseract. These languages must be part of your Tesseract installation.
 * You can <a href="https://code.google.com/p/tesseract-ocr/downloads/list">
 * download additional languages</a> form the Tesseract web site.</p>
 *
 * <p>When enabled, OCR is attempted on all supported image formats.  To
 * limit OCR to a subset of document content types, configure the corresponding
 * content-types (e.g. application/pdf, image/tiff, image/png, etc.).</p>
 *
 * {@nx.xml.usage
 * <documentParserFactory
 *        class="com.norconex.importer.parser.GenericDocumentParserFactory">
 *
 *     <ocr path="(path to Tesseract OCR software executable)">
 *         <languages>
 *             (optional coma-separated list of Tesseract languages)
 *         </languages>
 *         <contentTypes>
 *             (optional regex matching content types to limit OCR on)
 *         </contentTypes>
 *     </ocr>
 *
 *     <ignoredContentTypes>
 *         (optional regex matching content types to ignore for parsing,
 *          i.e., not parsed)
 *     </ignoredContentTypes>
 *
 *     <embedded>
 *         <splitContentTypes>
 *             (optional regex matching content types of containing files
 *              you want to "split" and have their embedded documents
 *              treated as individual documents)
 *         </splitContentTypes>
 *         <noExtractEmbeddedContentTypes>
 *             (optional regex matching content types of embedded files you do
 *              not want to extract from containing documents, regardless of
 *              the container content type)
 *         </noExtractEmbeddedContentTypes>
 *         <noExtractContainerContentTypes>
 *             (optional regex matching content types of containing files you
 *              do not want to see their embedded files extracted, regardless
 *              of the embedded content types)
 *         </noExtractContainerContentTypes>
 *     </embedded>
 *
 *     <fallbackParser
 *         class="(optionally overwrite the fallback parser)" />
 *
 *     <parsers>
 *         <!-- Optionally overwrite default parsers.
 *              You can configure many parsers. -->
 *         <parser
 *             contentType="(content type)"
 *             class="(DocumentParser implementing class)" />
 *     </parsers>
 *
 * </documentParserFactory>
 * }
 * <h4>Usage example:</h4>
 * <p>
 * The following uses Tesseract to convert English and French images in PDF
 * into text and it will also extract documents from Zip files and treat
 * them as separate documents.
 * </p>
 * {@nx.xml.example
 * <documentParserFactory>
 *     <ocr path="/app/ocr/tesseract.exe">
 *         <languages>en, fr</languages>
 *         <contentTypes>application/pdf</contentTypes>
 *     </ocr>
 *     <embedded>
 *         <splitContentTypes>application/zip</splitContentTypes>
 *     </embedded>
 * </documentParserFactory>
 * }
 */
public class GenericDocumentParserFactory
        implements DocumentParserFactory, XMLConfigurable {

    private static final Logger LOG =
            LoggerFactory.getLogger(GenericDocumentParserFactory.class);

    private final Map<ContentType, DocumentParser> parsers =
            new HashMap<>();
    private final ParseHints parseHints = new ParseHints();
    private DocumentParser fallbackParser;

    private String ignoredContentTypesRegex;

    private boolean parsersAreUpToDate = false;

    /**
     * Creates a new document parser factory of the given format.
     */
    public GenericDocumentParserFactory() {
        fixTikaInitWarning();

        //have all parsers lazy loaded instead?
        initDefaultParsers();
    }

    private void fixTikaInitWarning() {

        // A check for Tesseract OCR parser is done the first time a Tika
        // parser is used.  We remove this check since we manage Tesseract OCR
        // via Importer config only.
        try {
            FieldUtils.writeStaticField(
                    TesseractOCRParser.class, "HAS_WARNED", true, true);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            LOG.warn("Could not disable invalid Tessaract OCR warning. "
                   + "If you see such warning, you can ignore.");
        }

        // A check for SQL-Lite is also done and we do not want it.
        try {
            FieldUtils.writeStaticField(
                    SQLite3Parser.class, "HAS_WARNED", true, true);
        } catch (IllegalAccessException | IllegalArgumentException e) {
            LOG.warn("Could not disable \"sqlite-jdbc\" warning. "
                   + "If you see such warning, you can ignore.");
        }
    }

    protected void initDefaultParsers() {
        // Fallback parser
        fallbackParser = new FallbackParser();

        //TODO delete when released in Tika:
        //https://issues.apache.org/jira/browse/TIKA-2222
        // PureEdge XFDL
        parsers.put(
                ContentType.valueOf("application/vnd.xfdl"), new XFDLParser());
    }

    /**
     * Gets parse hints.
     * @return parse hints
         */
    public ParseHints getParseHints() {
        return parseHints;
    }

    /**
     * Registers a parser to use for the given content type. The provided
     * parser will never be used if the content type
     * is ignored by {@link #getIgnoredContentTypesRegex()}.
     * @param contentType content type
     * @param parser parser
         */
    public void registerParser(
            ContentType contentType, DocumentParser parser) {
        parsers.put(contentType, parser);
    }

    /**
     * Gets a parser based on content type, regardless of document reference
     * (ignoring it).
     * All parsers are assumed to have been configured properly
     * before the first call to this method.
     */
    @Override
    public final DocumentParser getParser(
            String documentReference, ContentType contentType) {
        // If ignoring content-type, do not even return a parser
        if (contentType != null
                && StringUtils.isNotBlank(ignoredContentTypesRegex)
                && contentType.toString().matches(ignoredContentTypesRegex)) {
            return null;
        }

        ensureParseHintsState();
        var parser = parsers.get(contentType);
        if (parser == null) {
            return fallbackParser;
        }
        return parser;
    }

    /**
     * Gets the regular expression matching content types to ignore
     * (i.e. do not perform parsing on them).
     * @return regular expression
     */
    public String getIgnoredContentTypesRegex() {
        return ignoredContentTypesRegex;
    }
    /**
     * sets the regular expression matching content types to ignore
     * (i.e. do not perform parsing on them).
     * @param ignoredContentTypesRegex regular expression
     */
    public void setIgnoredContentTypesRegex(String ignoredContentTypesRegex) {
        this.ignoredContentTypesRegex = ignoredContentTypesRegex;
    }

    private synchronized void ensureParseHintsState() {
        if (!parsersAreUpToDate) {
            for (Entry<ContentType, DocumentParser> entry :
                parsers.entrySet()) {
                var parser = entry.getValue();
                initParseHints(parser);
            }
            initParseHints(fallbackParser);
            parsersAreUpToDate = true;
            validateOCRInstall();
        }
    }
    private void initParseHints(DocumentParser parser) {
        if (parser instanceof HintsAwareParser p) {
            p.initialize(parseHints);
        }
    }

    //TODO Should this be a generic utility method?
    //TODO Validate languagues in config matches those installed.
    //TODO Print out Tesseract version and path on startup?
    private void validateOCRInstall() {
        var ocrConfig = parseHints.getOcrConfig();
        if (StringUtils.isBlank(ocrConfig.getPath())) {
            LOG.debug("OCR parsing is disabled (no path provided).");
            return;
        }
        var exeFile = new File(ocrConfig.getPath());
        if (exeFile.isDirectory()) {
            exeFile = new File(exeFile,
                    (System.getProperty("os.name").startsWith("Windows")
                                    ? "tesseract.exe" : "tesseract"));
            LOG.warn("""
            	As of Norconex Importer 2.10.0, it is recommended to\s\
            	specify the full path to Tesseract executable as\s\
            	opposed to a folder. We will try to assume the\s\
            	for now: {}""", exeFile.getAbsolutePath());
        }
        if (!exeFile.exists()) {
            LOG.error("OCR path specified but the Tesseract executable "
                    + "was not found: {}", exeFile.getAbsolutePath());
        } else if (!exeFile.isFile()) {
            LOG.error("OCR path does not point to a file: {}",
                    exeFile.getAbsolutePath());
        } else {
            LOG.info("OCR parsing is enabled.");
        }
    }


    @Override
    public void loadFromXML(XML xml) {
        setIgnoredContentTypesRegex(xml.getString(
                "ignoredContentTypes", ignoredContentTypesRegex));

        // Parse hints
        loadParseHintsFromXML(xml);

        // Fallback parser
        fallbackParser = xml.getObjectImpl(DocumentParser.class,
                "fallbackParser", fallbackParser);

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
    }

    private void loadParseHintsFromXML(XML xml) {
        // Embedded Config
        var embXml = xml.getXML("embedded");
        if (embXml != null) {
            var embCfg = parseHints.getEmbeddedConfig();
            embCfg.setSplitContentTypes(
                    embXml.getString("splitContentTypes", null));
            embCfg.setNoExtractContainerContentTypes(
                    embXml.getString("noExtractContainerContentTypes", null));
            embCfg.setNoExtractEmbeddedContentTypes(
                    embXml.getString("noExtractEmbeddedContentTypes", null));
        }

        // OCR Config
        var ocrXml = xml.getXML("ocr");
        if (ocrXml != null) {
            var ocrCfg = parseHints.getOcrConfig();
            ocrCfg.setPath(ocrXml.getString("@path"));
            ocrCfg.setLanguages(ocrXml.getString("languages"));
            ocrCfg.setContentTypes(ocrXml.getString("contentTypes"));
        }
    }


    @Override
    public void saveToXML(XML xml) {
        if (ignoredContentTypesRegex != null) {
            xml.addElement("ignoredContentTypes", ignoredContentTypesRegex);
        }

        saveParseHintsToXML(xml);

        xml.addElement("fallbackParser", fallbackParser);

        if (!parsers.isEmpty()) {
            var parsersXML = xml.addElement("parsers");

            for (Entry<ContentType, DocumentParser> entry:
                    parsers.entrySet()) {
                parsersXML.addElement("parser", entry.getValue())
                        .setAttribute("contentType", entry.getKey().toString());
            }
        }
    }

    private void saveParseHintsToXML(XML xml) {
        var emb = parseHints.getEmbeddedConfig();
        if (!emb.isEmpty()) {
            var embXML = xml.addElement("embedded");
            embXML.addElement("splitContentTypes", emb.getSplitContentTypes());
            embXML.addElement("noExtractEmbeddedContentTypes",
                    emb.getNoExtractEmbeddedContentTypes());
            embXML.addElement("noExtractContainerContentTypes",
                    emb.getNoExtractContainerContentTypes());
        }
        var ocr = parseHints.getOcrConfig();
        if (!ocr.isEmpty()) {
            var ocrXML = xml.addElement("ocr");
            ocrXML.setAttribute("path", ocr.getPath());
            ocrXML.addElement("languages", ocr.getLanguages());
            ocrXML.addElement("contentTypes", ocr.getContentTypes());
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (!(other instanceof GenericDocumentParserFactory castOther) || !new EqualsBuilder()
                .append(ignoredContentTypesRegex,
                        castOther.ignoredContentTypesRegex)
                .append(parseHints, castOther.parseHints)
                .append(parsersAreUpToDate, castOther.parsersAreUpToDate)
                .append(parsers.size(), castOther.parsers.size())
                .append(fallbackParser, castOther.fallbackParser)
                .isEquals()) {
            return false;
        }

        for (Entry<ContentType, DocumentParser> entry : parsers.entrySet()) {
            var otherParser = castOther.parsers.get(entry.getKey());
            if (otherParser == null) {
                return false;
            }
            var parser = entry.getValue();
            if (!Objects.equals(parser,  otherParser)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        var hash = new HashCodeBuilder()
                .append(ignoredContentTypesRegex)
                .append(parseHints)
                .append(parsersAreUpToDate)
                .append(parsers.size())
                .toHashCode();
        hash += fallbackParser.hashCode();
        for (Entry<ContentType, DocumentParser> entry : parsers.entrySet()) {
            var ct = entry.getKey();
            hash += ct.hashCode();
            var parser = entry.getValue();
            if (parser == null) {
                continue;
            }
            hash += parser.hashCode();
        }
        return hash;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("namedParsers", parsers)
                .append("fallbackParser", fallbackParser)
                .append("ignoredContentTypesRegex", ignoredContentTypesRegex)
                .append("parseHints", parseHints)
                .append("parsersAreUpToDate", parsersAreUpToDate)
                .toString();
    }
}
