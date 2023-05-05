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
package com.norconex.importer.parser.impl;

import static com.norconex.importer.doc.DocMetadata.EMBEDDED_REFERENCE;
import static com.norconex.importer.doc.DocMetadata.EMBEDDED_TYPE;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.ZeroByteFileException;
import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.metadata.filter.FieldNameMappingFilter;
import org.apache.tika.mime.MediaType;
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

import com.google.common.base.Objects;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLUtil;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocRecord;
import com.norconex.importer.parser.DocumentParser;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.ParseOptions;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;


/**
 * Base parse class wrapping an Apache Tika parser for use by the importer.
 * Cache options are cached.
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class AbstractTikaParser implements DocumentParser {

    private final Function<TikaConfig, Parser> tikaParserProvider;

    //TODO make sure OCR content types is applied

    // Provide default settings in case the parser was not initialized
    // for some reason.
    private ParseOptions parseOptions = new ParseOptions();
    private Parser tikaParser;
    private ThreadSafeCacheableAutoDetectWrapper knownDetector;

    /**
     * Creates a new Tika-based parser.
     * @param tikaParserProvider non-<code>null</code> provider of a Tika parser
     */
    public AbstractTikaParser(
            @NonNull Function<TikaConfig, Parser> tikaParserProvider) {
        this.tikaParserProvider = tikaParserProvider;
        fixTikaInitWarning();
    }

    protected ParseOptions getParseOptions() {
        return parseOptions;
    }

    @Override
    public void init(@NonNull ParseOptions parseOptions)
            throws DocumentParserException {
        this.parseOptions = parseOptions;
        var tikaConfig = configureTika(parseOptions);
        tikaParser = tikaParserProvider.apply(tikaConfig);
        if (tikaParser instanceof AutoDetectParser p) {
            knownDetector =
                    new ThreadSafeCacheableAutoDetectWrapper(p.getDetector());
            p.setDetector(knownDetector);
        } else {
            knownDetector = null;
        }
    }

    @Override
    public final List<Doc> parseDocument(Doc doc, Writer output)
            throws DocumentParserException {

        var tikaMetadata = new Metadata();
        if (doc.getDocRecord().getContentType() == null) {
            throw new DocumentParserException(
                    "Doc must have a content-type.");
        }
        var contentType = doc.getDocRecord().getContentType().toString();
        //TODO getContent() here does a rewind just to get stream
        //which may be an unnecessary read.  Have stream factory
        //directly on document instead to save a read?
        var content = doc.getInputStream();

        tikaMetadata.set(HttpHeaders.CONTENT_TYPE, contentType);
        tikaMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY,
                doc.getReference());
        tikaMetadata.set(HttpHeaders.CONTENT_ENCODING,
                doc.getDocRecord().getContentEncoding());
        tikaMetadata.set(HttpHeaders.CONTENT_LENGTH,
                Long.toString(content.length()));

        RecursiveParser recursiveParser = null;
        try {
            if (knownDetector != null) {
                knownDetector.initCache(doc.getReference(), contentType);
            }

            recursiveParser = createRecursiveParser(
                    doc.getReference(), contentType, output, doc.getMetadata(),
                    content.getStreamFactory());
            var context = new ParseContext();
            context.set(Parser.class, recursiveParser);

            var pdfConfig = new PDFParserConfig();
            //var ocrConfig = parseHints.getOcrConfig();
            if (!parseOptionsOrThrow().getOcrConfig().isDisabled()) {
                // Tesseract config should already be on context if enabled,
                // thanks to configured TikaConfig
//                    && StringUtils.isNotBlank(ocrConfig.getPath())
//                    && (StringUtils.isBlank(ocrConfig.getContentTypes())
//                        || contentType.matches(ocrConfig.getContentTypes()))) {
//                context.set(TesseractOCRConfig.class, ocrTesseractConfig);
                pdfConfig.setExtractInlineImages(true);
            } else {
                pdfConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
            }
            pdfConfig.setSuppressDuplicateOverlappingText(true);
            context.set(PDFParserConfig.class, pdfConfig);
            modifyTikaParseContext(context);

            recursiveParser.parse(content,
                    new BodyContentHandler(output),  tikaMetadata, context);
        } catch (ZeroByteFileException e) {
            LOG.warn("Document has no content: " + doc.getReference());
        } catch (Exception e) {
            throw new DocumentParserException(e);
        }
        return recursiveParser.getEmbeddedDocuments();
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


    protected void addTikaMetadataToImporterMetadata(
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
            String reference, String contentType, Writer writer,
            Properties metadata, CachedStreamFactory streamFactory) {
        if (parseOptionsOrThrow()
                .getEmbeddedConfig()
                .getSplitEmbeddedOf()
                .stream()
                .anyMatch(tm -> tm.test(contentType))) {
            return new SplitEmbbededParser(
                    reference, tikaParser, metadata, streamFactory);
        }
        return new MergeEmbeddedParser(tikaParser, writer, metadata);
    }

    protected class SplitEmbbededParser
            extends ParserDecorator implements RecursiveParser {
        private static final long serialVersionUID = -5011890258694908887L;
        private final String reference;
        private final Properties metadata;
        private final transient CachedStreamFactory streamFactory;
        private boolean isMasterDoc = true;
        private String masterType;
        private int embedCount;
        private final List<Doc> embeddedDocs = new ArrayList<>();
        public SplitEmbbededParser(String reference, Parser parser,
                Properties metadata, CachedStreamFactory streamFactory) {
            super(parser);
            this.streamFactory = streamFactory;
            this.reference = reference;
            this.metadata = metadata;
        }
        @Override
        public void parse(InputStream stream, ContentHandler handler,
                Metadata tikaMeta, ParseContext context)
                        throws IOException, SAXException, TikaException {

            if (isMasterDoc) {
                isMasterDoc = false;
                if (isSkipEmbeddedConfigured()) {
                    //TODO only if not known?
                    masterType =
                            knownDetector.detect(stream, tikaMeta).toString();
                }
                super.parse(stream, handler, tikaMeta, context);
                addTikaMetadataToImporterMetadata(tikaMeta, metadata);
            } else {
                if (isSkipEmbeddedConfigured()) {
                    var currentType =
                            knownDetector.detect(stream, tikaMeta).toString();
                    if (!performExtract(masterType, currentType)) {
                        // do not extract this embedded doc
                        return;
                    }
                }

                embedCount++;

                var embedMeta = new Properties();
                addTikaMetadataToImporterMetadata(tikaMeta, embedMeta);

                var embedDocInfo = resolveEmbeddedResourceName(
                        tikaMeta, embedMeta, embedCount);

                // Read the steam into cache for reuse since Tika will
                // close the original stream on us causing exceptions later.
                try (var embedOutput = streamFactory.newOuputStream()) {
                    IOUtils.copy(stream, embedOutput);
                    var embedInput = embedOutput.getInputStream();
                    embedDocInfo.addEmbeddedParentReference(reference);
                    var embedDoc = new Doc(embedDocInfo, embedInput, embedMeta);
//                  embedMeta.setReference(embedRef);
//                  embedMeta.setEmbeddedParentReference(reference);

//                  String rootRef = metadata.getEmbeddedParentRootReference();
//                  if (StringUtils.isBlank(rootRef)) {
//                      rootRef = reference;
//                  }
//                  embedMeta.setEmbeddedParentRootReference(rootRef);
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

            var docRecord = new DocRecord();

            String name;

            // Package item file name (e.g. a file in a zip)
            name = tikaMeta.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
            if (StringUtils.isNotBlank(name)) {
                docRecord.setReference(reference + "!" + name);
                embedMeta.set(EMBEDDED_REFERENCE, name);
                embedMeta.set(EMBEDDED_TYPE, "package-file");


//                docInfo.setEmbeddedReference(name);
//                docInfo.setEmbeddedType("package-file");
//                embedMeta.setEmbeddedReference(name);
//                embedMeta.setEmbeddedType("package-file");
//                return name;
                return docRecord;
            }

            // Name of Embedded file in regular document
            // (e.g. excel file in a word doc)
            name = tikaMeta.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            if (StringUtils.isNotBlank(name)) {
                docRecord.setReference(reference + "!" + name);

                embedMeta.set(EMBEDDED_REFERENCE, name);
                embedMeta.set(EMBEDDED_TYPE, "file-file");

//                docInfo.setEmbeddedReference(name);
//                docInfo.setEmbeddedType("package-file");
//                embedMeta.setEmbeddedReference(name);
//                embedMeta.setEmbeddedType("file-file");
//                return name;
                return docRecord;
            }

            // Name of embedded content in regular document
            // (e.g. image with no name in a word doc)
            // Make one up with content type
            // (which should be OK most of the time).
            name = tikaMeta.get(HttpHeaders.CONTENT_TYPE);
            if (StringUtils.isNotBlank(name)) {
                var ct = ContentType.valueOf(name);
                if (ct != null) {
                    var embedRef =
                            "embedded-" + embedCount + "." + ct.getExtension();

                    docRecord.setReference(reference + "!" + embedRef);

                    embedMeta.set(EMBEDDED_REFERENCE, embedRef);
                    embedMeta.set(EMBEDDED_TYPE, "file-file");

//                    docInfo.setEmbeddedType("file-object");
//                    embedMeta.setEmbeddedType("file-object");
//                    return "embedded-" + embedCount + "." + ct.getExtension();
                    return docRecord;
                }
            }

            // Default... we could not find any name so make a unique one.
//            embedMeta.setEmbeddedType("unknown");

            var embedRef =
                    "embedded-" + embedCount + ".unknown";

            embedMeta.set(EMBEDDED_REFERENCE, embedRef);
            embedMeta.set(EMBEDDED_TYPE, "unknown");

//            docInfo.setEmbeddedType("unknown");
            docRecord.setReference(reference + "!" + embedRef);
//                    + "embedded-" + embedCount + ".unknown");
            return docRecord;
        }
    }

    protected class MergeEmbeddedParser
            extends ParserDecorator implements RecursiveParser  {
        private static final long serialVersionUID = -5011890258694908887L;
        private final Writer writer;
        private final Properties metadata;

        private final LinkedList<String> hierarchy = new LinkedList<>();


        public MergeEmbeddedParser(Parser parser,
                Writer writer, Properties metadata) {
            super(parser);
            this.writer = writer;
            this.metadata = metadata;
        }
        @Override
        public void parse(InputStream stream, ContentHandler handler,
                Metadata tikaMeta, ParseContext context)
                throws IOException, SAXException, TikaException {
            var performExtract = true;
            if (isSkipEmbeddedConfigured()) {
                var parentType = hierarchy.peekLast();
                var currentType =
                        knownDetector.detect(stream, tikaMeta).toString();
                hierarchy.add(currentType);
                performExtract = performExtract(parentType, currentType);
            }
            if (performExtract) {
                super.parse(stream,
                        new BodyContentHandler(writer), tikaMeta, context);
                addTikaMetadataToImporterMetadata(tikaMeta, metadata);
            }
            if (isSkipEmbeddedConfigured()) {
                hierarchy.pollLast();
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


    private boolean isSkipEmbeddedConfigured() {
        var embCfg = parseOptionsOrThrow().getEmbeddedConfig();
        return !embCfg.getSkipEmmbbeded().isEmpty()
                || !embCfg.getSkipEmmbbededOf().isEmpty();
    }

    private boolean performExtract(String parentType, String currentType) {
        if (parentType == null) {
            return true;
        }

        //--- Container ---
        return parseOptionsOrThrow()
                .getEmbeddedConfig()
                .getSkipEmmbbededOf()
                .stream()
                .noneMatch(tm -> tm.test(parentType))
        //--- Embedded ---
            && parseOptionsOrThrow()
                .getEmbeddedConfig()
                .getSkipEmmbbeded()
                .stream()
                .noneMatch(tm -> tm.test(currentType));
    }


    //TODO create in a separate class, and make it that it caches
    // embedded detections as well, with the cash reset upon setting
    // a new thread-safe reference via a new cacheReference(ref, contentType).
    // Class name:

    // Then use this to autodetect before actual parsing to skip embedded
    // if configured to do so.

    /**
     * This class prevents detecting the content type a second time when
     * we already know what it is.  Only the content type for the root
     * reference is not detected again. Any embedded documents will have
     * default detection behavior applied on them.
     */
    class ThreadSafeCacheableAutoDetectWrapper implements Detector {
        private static final long serialVersionUID = 225979407457365951L;
        private final Detector originalDetector;
        private final ThreadLocal<Cache> threadCache = new ThreadLocal<>();
        public ThreadSafeCacheableAutoDetectWrapper(Detector originalDetector) {
            this.originalDetector = originalDetector;
        }
        void initCache(String reference, String contentType) {
            var cache = new Cache();
            cache.rootReference = reference;
            cache.currentReference = reference;
            if (StringUtils.isNotBlank(contentType)) {
                cache.currentType = new MediaType(
                        StringUtils.substringBefore(contentType, "/"),
                        StringUtils.substringAfter(contentType, "/"));
            }
            threadCache.set(cache);
        }
        @Override
        public MediaType detect(InputStream input, Metadata metadata)
                throws IOException {
            var cache = threadCache.get();

            var ref = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
            var embedId = metadata.get(
                    TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
            if (StringUtils.isNotBlank(embedId)) {
                ref = cache.rootReference + "__EMB__" + ref;
            }
            if (StringUtils.isBlank(ref)) {
                ref = cache.rootReference + "__hash__" + input.hashCode();
            }

            if (cache.currentType == null
                    || !Objects.equal(cache.currentReference, ref)) {
                cache.currentReference = ref;
                cache.currentType = originalDetector.detect(input, metadata);
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Caching new media type : "
                            + ref + " => " + cache.currentType);
                }
            } else if (LOG.isTraceEnabled()) {
                LOG.trace("Using cached media type: "
                        + ref + " => " + cache.currentType);
            }
            return cache.currentType;
        }
        class Cache {
            private String rootReference;
            private String currentReference;
            private MediaType currentType;
        }
    }

    // Useful: https://tika.apache.org/2.7.0/configuring.html
    // and: https://cwiki.apache.org/confluence/display/tika/TikaOCR
    // and: https://cwiki.apache.org/confluence/display/TIKA/Metadata+Filters
    protected TikaConfig configureTika(@NonNull ParseOptions opts)
            throws DocumentParserException {
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
                      <mappings>
                        <mapping from="dc:title" to="title"/>
                      </mappings>
                    </params>
                  </metadataFilter>
                </metadataFilters>
                """.formatted(FieldNameMappingFilter.class.getName()));

            var parsersXml = tikaXml.addElement("parsers");
            var ocr = opts.getOcrConfig();

            // If nothing to configure, return default
            if (ocr.isDisabled()) {
                return new TikaConfig();
            }
            // Configure Tesseract OCR
            parsersXml.addXML("""
                <parser class="%s">
                    <parser-exclude class="%s"/>
                </parser>
                """.formatted(
                        org.apache.tika.parser.DefaultParser.class.getName(),
                        TesseractOCRParser.class.getName()));
            var t = ocr.getTesseractConfig();
            parsersXml.addXML(new TesseractParserConfigBuilder()
                .append("applyRotation", t.isApplyRotation())
                .append("colorSpace", t.getColorspace())
                .append("density", t.getDensity())
                .append("depth", t.getDepth())
                .append("enableImagePreprocessing",
                        t.isEnableImagePreprocessing())
                .append("filter", t.getFilter())
                .append("imageMagickPath", ocr.getImageMagickPath())
                .append("language", t.getLanguage())
                .append("maxFileSizeToOcr", t.getMaxFileSizeToOcr())
                .append("minFileSizeToOcr", t.getMinFileSizeToOcr())
                .append("pageSegMode", t.getPageSegMode())
                .append("pageSeparator", t.getPageSeparator())
                .append("preserveInterwordSpacing",
                        t.isPreserveInterwordSpacing())
                .append("resize", t.getResize())
                .append("skipOcr", ocr.isDisabled())
                .append("tessdataPath", ocr.getTessdataPath())
                .append("tesseractPath", ocr.getTesseractPath())
                .append("timeoutSeconds", t.getTimeoutSeconds())
                .build());
            return new TikaConfig((Element) tikaXml.getNode());
        } catch (TikaException | IOException e) {
            throw new DocumentParserException("Could not configure Tika.", e);
        }
    }

    private ParseOptions parseOptionsOrThrow() {
        if (parseOptions != null) {
            return parseOptions;
        }
        throw new IllegalStateException(
                "%s was not initialized. Was #init(ParseOptions) called?"
                    .formatted(getClass().getName()));
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
        TesseractParserConfigBuilder append(String name, boolean value) {
            return append(name, "bool", Boolean.toString(value));
        }
        TesseractParserConfigBuilder append(String name, String value) {
            return append(name, "string", value);
        }
        TesseractParserConfigBuilder append(String name, int value) {
            return append(name, "int", Integer.toString(value));
        }
        TesseractParserConfigBuilder append(String name, long value) {
            return append(name, "long", Long.toString(value));
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
