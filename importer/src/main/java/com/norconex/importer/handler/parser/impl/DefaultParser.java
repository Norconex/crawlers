/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.importer.handler.parser.impl;

import static com.norconex.importer.doc.DocMetadata.EMBEDDED_REFERENCE;
import static com.norconex.importer.doc.DocMetadata.EMBEDDED_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.FieldNameMappingFilter;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.parser.ParserDecorator;
import org.apache.tika.parser.ocr.TesseractOCRParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.w3c.dom.Element;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLUtil;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocRecord;
import com.norconex.importer.handler.BaseDocumentHandler;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.util.MatchUtil;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;


/**
 * Parser class when no other handlers are specified.
 * The importer uses Apache Tika parser in its own way with default
 * settings common for most senarios.
 * If you want to use and configure Tika yourself, use
 * TikaParser.
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class DefaultParser
        extends BaseDocumentHandler
        implements Configurable<DefaultParserConfig> {

    @Getter
    private final DefaultParserConfig configuration = new DefaultParserConfig();
    private AutoDetectParser tikaParser;

    @Override
    public void init() throws IOException {
        fixTikaInitWarning();
        tikaParser = new AutoDetectParser(configureTika());
    }


    //TODO document this one is based on tika parser, and only support
    // pre-defined config option.
    // THEN, create a TikaParser which is a wrapper around, tika one,
    // which takes a raw tika configuration file.


    @Override
    public void handle(DocContext ctx) throws IOException {

        var tikaMetadata = new Metadata();
        var contentType = ctx.docRecord().getContentType();

        if (contentType == null) {
            throw new IOException("Doc must have a content-type.");
        }

        RecursiveParser recursiveParser = null;
        tikaMetadata.set(HttpHeaders.CONTENT_TYPE, contentType.toString());
        tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                ctx.reference());
        tikaMetadata.set(HttpHeaders.CONTENT_ENCODING,
                ofNullable(ctx.docRecord().getCharset())
                    .map(Charset::toString)
                    .orElse(null));

        try (var input = CachedInputStream.cache(ctx.input().inputStream());
                var output = ctx.output().writer(UTF_8))  {

            tikaMetadata.set(HttpHeaders.CONTENT_LENGTH,
                    Long.toString(input.length()));

            recursiveParser = createRecursiveParser(ctx, output);
            var context = new ParseContext();
            context.set(Parser.class, recursiveParser);

            var pdfConfig = new PDFParserConfig();
            if (!configuration.getOcr().isDisabled()) {
                pdfConfig.setExtractInlineImages(true);
            } else {
                pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
            }
            pdfConfig.setSuppressDuplicateOverlappingText(true);
            context.set(PDFParserConfig.class, pdfConfig);
            modifyTikaParseContext(context);

            recursiveParser.parse(input,
                    new BodyContentHandler(output),  tikaMetadata, context);
        } catch (ZeroByteFileException e) {
            LOG.warn("Document has no content: {}", ctx.reference());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException(e);
        }
        ctx.childDocs().addAll(recursiveParser.getEmbeddedDocuments());
    }

    /**
     * Override to apply your own settings on the Tika ParseContext.
     * The ParseContext is already configured before calling this method.
     * Changing existing settings may cause failure.
     * Only override if you know what you are doing.
     * The default implementation does nothing.
     * @param parseContext Tika parse context
     */
    protected void modifyTikaParseContext(ParseContext parseContext) {
        //NOOP
    }


    protected void addTikaToImporterMetadata(
            Metadata tikaMeta, Properties metadata) {
        var  names = tikaMeta.names();
        for (String name : names) {
            if (TikaCoreProperties.RESOURCE_NAME_KEY.equals(name)) {
                continue;
            }
            var nxValues = metadata.getStrings(name);
            var tikaValues = tikaMeta.getValues(name);
            for (String tikaValue : tikaValues) {
                if (!containsSameValue(name, nxValues, tikaValue)) {
                    metadata.add(name, tikaValue);
                } else {
                    metadata.set(name, tikaValue);
                }
            }
        }
    }

    private boolean containsSameValue(
            String name, List<String> nxValues, String tikaValue) {
        if (EqualsUtil.equalsAnyIgnoreCase(
                name,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.CONTENT_ENCODING)) {
            var tk = tikaValue.replaceAll("[\\s]", "");
            for (String nxValue : nxValues) {
                if (nxValue.replaceAll("[\\s]", "").equalsIgnoreCase(tk)) {
                    return true;
                }
            }
            return false;
        }
        return nxValues.contains(tikaValue);
    }

    protected RecursiveParser createRecursiveParser(
            DocContext docCtx, Writer output) {
        // if the current file (container) matches, we extract (split)
        // its embedded documents (else, we merge).
        if (MatchUtil.matchesContentType(
                configuration.getEmbedded().getContainerTypeMatcher(),
                docCtx.docRecord().getContentType())) {
            return new SplitEmbbededParser(tikaParser, docCtx);
        }
        return new MergeEmbeddedParser(tikaParser, docCtx, output);
    }

    protected class SplitEmbbededParser
            extends ParserDecorator implements RecursiveParser {
        private static final long serialVersionUID = 1L;
        private final transient DocContext rootDocCtx;
        private boolean isMasterDoc = true;
        private int embedCount;
        private final List<Doc> embeddedDocs = new ArrayList<>();

        public SplitEmbbededParser(Parser parser, DocContext docCtx) {
            super(parser);
            rootDocCtx = docCtx;
        }

        @Override
        public void parse(InputStream input, ContentHandler handler,
                Metadata tikaMeta, ParseContext context)
                        throws IOException, SAXException, TikaException {

            // Container doc:
            if (isMasterDoc) {
                isMasterDoc = false;
                super.parse(input, handler, tikaMeta, context);
                addTikaToImporterMetadata(tikaMeta, rootDocCtx.metadata());
                return;
            }

            // Embedded doc:
            var embedContentType = ContentTypeDetector.detect(input);
            if (MatchUtil.matchesContentType(
                    configuration.getEmbedded().getEmbeddedTypeMatcher(),
                    embedContentType)) {
                embedCount++;
                var embedMeta = new Properties();
                addTikaToImporterMetadata(tikaMeta, embedMeta);
                var embedDocInfo = resolveEmbeddedResourceName(
                        tikaMeta, embedMeta, embedCount);

                // Read the steam into cache for reuse since Tika will
                // close the original stream on us causing exceptions later.
                try (var embedOutput =
                        rootDocCtx.streamFactory().newOuputStream()) {
                    IOUtils.copy(input, embedOutput);
                    var embedInput = embedOutput.getInputStream();
                    embedDocInfo.addEmbeddedParentReference(
                            rootDocCtx.reference());
                    var embedDoc = new Doc(embedDocInfo, embedInput, embedMeta);
                    embeddedDocs.add(embedDoc);
                }
            }
        }

        @Override
        public List<Doc> getEmbeddedDocuments() {
            return embeddedDocs;
        }

        private DocRecord resolveEmbeddedResourceName(
                Metadata tikaMeta, Properties embedMeta, int embedCount) {

            new DocRecord();
            rootDocCtx.reference();
            String name;

            // Package item file name (e.g. a file in a zip)
            name = tikaMeta.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
            if (StringUtils.isNotBlank(name)) {
                return docRecord(name, embedMeta, "package-file");
            }

            // Name of Embedded file in regular document
            // (e.g. excel file in a word doc)
            name = tikaMeta.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (StringUtils.isNotBlank(name)) {
                return docRecord(name, embedMeta, "file-file");
            }

            // Name of embedded content in regular document
            // (e.g. image with no name in a word doc)
            // Make one up with content type
            // (which should be OK most of the time).
            name = tikaMeta.get(HttpHeaders.CONTENT_TYPE);
            if (StringUtils.isNotBlank(name)) {
                var ct = ContentType.valueOf(name);
                if (ct != null) {
                    return docRecord(
                            "embedded-" + embedCount + "." + ct.getExtension(),
                            embedMeta,
                            "file-file");
                }
            }

            // Default... we could not find any name so make a unique one.
            return docRecord(
                    "embedded-" + embedCount + ".unknown",
                    embedMeta,
                    "unknown");
        }
        private DocRecord docRecord(
                String embedRef, Properties embedMeta, String embedType) {
            var docRecord = new DocRecord();
            docRecord.setReference(rootDocCtx.reference() + "!" + embedRef);
            embedMeta.set(EMBEDDED_REFERENCE, embedRef);
            embedMeta.set(EMBEDDED_TYPE, embedType);
            return docRecord;
        }
    }

    protected class MergeEmbeddedParser
            extends ParserDecorator implements RecursiveParser  {
        private static final long serialVersionUID = -5011890258694908887L;
        private final transient Writer writer;
        private final Properties metadata;

        public MergeEmbeddedParser(Parser parser,
                DocContext docCtx, Writer writer) {
            super(parser);
            this.writer = writer;
            metadata = docCtx.metadata();
        }
        @Override
        public void parse(InputStream stream, ContentHandler handler,
                Metadata tikaMeta, ParseContext context)
                throws IOException, SAXException, TikaException {
            var currentType = ContentTypeDetector.detect(stream,
                    tikaMeta.get(TikaCoreProperties.RESOURCE_NAME_KEY));
            if (MatchUtil.matchesContentType(
                    configuration.getEmbedded().getEmbeddedTypeMatcher(),
                    currentType)) {
                super.parse(stream,
                        new BodyContentHandler(writer), tikaMeta, context);
                addTikaToImporterMetadata(tikaMeta, metadata);
            }
        }
        @Override
        public List<Doc> getEmbeddedDocuments() {
            return Collections.emptyList();
        }
    }

    protected interface RecursiveParser extends Parser {
        // never null
        List<Doc> getEmbeddedDocuments();
    }

    // Useful: https://tika.apache.org/2.7.0/configuring.html
    // and: https://cwiki.apache.org/confluence/display/tika/TikaOCR
    // and: https://cwiki.apache.org/confluence/display/TIKA/Metadata+Filters
    protected TikaConfig configureTika() throws IOException {
        try {
            var docBuilderfactory = XMLUtil.createDocumentBuilderFactory();
            docBuilderfactory.setNamespaceAware(true);
            var tikaXml = XML.of("properties")
                    .setDocumentBuilderFactory(docBuilderfactory)
                    .create();

            //TODO How to handle Tika 2.x changing original metadata
            // to different ones? Here we do title being an obvious one
            // people expects from HTML.
            // shall we do our own normalization above those standards?
            // Adopt and document tika normalization rules?

            //TODO why the following isn't working?
            tikaXml.addXML("""
                <metadataFilters>
                  <metadataFilter class="%s">
                    <params>
                      <excludeUnmapped>false</excludeUnmapped>
                      <!--
                      <mappings>
                        <mapping from="dc:title" to="title"/>
                      </mappings>
                      -->
                    </params>
                  </metadataFilter>
                </metadataFilters>
                """.formatted(FieldNameMappingFilter.class.getName()));

            var parsersXml = tikaXml.addElement("parsers");
            var ocr = configuration.getOcr();

            // If nothing to configure, return default
            if (ocr.isDisabled()) {
                return new TikaConfig();
            }

//            https://cwiki.apache.org/confluence/display/TIKA/TikaOCR

            // Configure Tesseract OCR
            parsersXml.addXML("""
                <parser class="%s">
                    <parser-exclude class="%s"/>
                </parser>
                """.formatted(
                        org.apache.tika.parser.DefaultParser.class.getName(),
                        TesseractOCRParser.class.getName()));
            parsersXml.addXML(new TesseractParserConfigBuilder()
                .append("applyRotation", ocr.getApplyRotation())
                .append("colorSpace", ocr.getColorSpace())
                .append("density", ocr.getDensity())
                .append("depth", ocr.getDepth())
                .append("enableImagePreprocessing",
                        ocr.getEnableImagePreprocessing())
                .append("filter", ocr.getFilter())
                .append("imageMagickPath", ocr.getImageMagickPath())
                .append("language", ocr.getLanguage())
                .append("maxFileSizeToOcr", ocr.getMaxFileSizeToOcr())
                .append("minFileSizeToOcr", ocr.getMinFileSizeToOcr())
                .append("pageSegMode", ocr.getPageSegMode())
                .append("pageSeparator", ocr.getPageSeparator())
                .append("preserveInterwordSpacing",
                        ocr.getPreserveInterwordSpacing())
                .append("resize", ocr.getResize())
                .append("skipOcr", ocr.isDisabled())
                .append("tessdataPath", ocr.getTessdataPath())
                .append("tesseractPath", ocr.getTesseractPath())
                .append("timeoutSeconds", ocr.getTimeoutSeconds())
                .build());
            return new TikaConfig((Element) tikaXml.getNode());
        } catch (TikaException e) {
            throw new IOException("Could not configure Tika.", e);
        }
    }

    private void fixTikaInitWarning() {
        //TODO is below still needed?
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
    }

    static class TesseractParserConfigBuilder {
        private final XML parser = new XML("parser")
                .setAttribute("class", TesseractOCRParser.class.getName());
        private final XML params = parser.addElement("params");
        TesseractParserConfigBuilder append(String name, Boolean value) {
            if (value != null) {
                return append(name, "bool", Boolean.toString(value));
            }
            return this;
        }
        TesseractParserConfigBuilder append(String name, String value) {
            if (value != null) {
                return append(name, "string", value);
            }
            return this;
        }
        TesseractParserConfigBuilder append(String name, Integer value) {
            if (value != null) {
                return append(name, "int", Integer.toString(value));
            }
            return this;
        }
        TesseractParserConfigBuilder append(String name, Long value) {
            if (value != null) {
                return append(name, "long", Long.toString(value));
            }
            return this;
        }
        TesseractParserConfigBuilder append(String name, Path value) {
            return append(name, "string", Optional.ofNullable(value)
                    .map(Path::toAbsolutePath)
                    .map(Path::toString).orElse(null));
        }
        private TesseractParserConfigBuilder append(
                String name, String type, String value) {
            if (StringUtils.isNotBlank(value)) {
                params.addElement("param")
                    .setAttribute("name", name)
                    .setAttribute("type", type)
                    .setTextContent(value);
            }
            return this;
        }
        public XML build() {
            return parser;
        }
    }
}
