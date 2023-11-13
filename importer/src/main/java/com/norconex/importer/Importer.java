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
package com.norconex.importer;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.ContentFamily;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.importer.charset.CharsetDetector;
import com.norconex.importer.doc.ContentTypeDetector;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.doc.DocRecord;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.DocumentHandler;
import com.norconex.importer.handler.DocumentHandlerException;
import com.norconex.importer.response.ImporterResponse;
import com.norconex.importer.response.ImporterResponse.Status;
import com.norconex.importer.response.ImporterResponseProcessor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Principal class responsible for importing documents.
 * Refer to {@link ImporterConfig} for configuration options.
 * Thread-safe, and reusing the same instance is highly recommended.
 * @see ImporterConfig
 */
@Slf4j
@ToString
@EqualsAndHashCode
public class Importer implements Configurable<ImporterConfig> {

    @Getter
    private final ImporterConfig configuration;

    // Only used when using command-line or invoking
    // importDocument(ImporterRequest). The "doc" version has its own.
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private CachedStreamFactory requestStreamFactory;

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    @JsonIgnore
    private final EventManager eventManager;

    private static final InheritableThreadLocal<Importer> INSTANCE =
            new InheritableThreadLocal<>();

    /**
     * Creates a new importer with default configuration.
     */
    public Importer() {
        this(null);
    }
    /**
     * Creates a new importer with the given configuration.
     * @param importerConfig Importer configuration
     */
    public Importer(ImporterConfig importerConfig) {
        this(importerConfig, null);
    }
    /**
     * Creates a new importer with the given configuration.
     * @param importerConfig Importer configuration
     * @param eventManager event manager
     */
    public Importer(ImporterConfig importerConfig, EventManager eventManager) {
        if (importerConfig != null) {
            configuration = importerConfig;
        } else {
            configuration = new ImporterConfig();
        }
        this.eventManager = new EventManager(eventManager);
//        parseHandler = new ImporterParseHandler(this);
        INSTANCE.set(this);
    }

    @JsonIgnore
    public static Importer get() {
        return INSTANCE.get();
    }

    /**
     * Invokes the importer from the command line.
     * @param args Invoke it once without any arguments to get a
     *    list of command-line options.
     */
    public static void main(String[] args) {
        ImporterLauncher.launch(args);
    }

    /**
     * Gets the event manager.
     * @return event manager
     */
    public EventManager getEventManager() {
        return eventManager;
    }

    /**
     * Imports a document according to the importer configuration.
     * @param req request instructions for importing
     * @return importer response
         */
    public ImporterResponse importDocument(ImporterRequest req) {
        try {
            return importDocument(toDocument(req));
        } catch (ImporterException e) {
            LOG.warn("Importer request failed: {}", req, e);
            return new ImporterResponse()
                    .setReference(req.getReference())
                    .setStatus(ImporterResponse.Status.ERROR)
                    .setException(new ImporterException(
                            "Failed to import document for request: " + req, e))
                    .setDescription(e.getLocalizedMessage());
        }
    }
    /**
     * Imports a document according to the importer configuration.
     * @param document the document to import
     * @return importer response
     */
    public ImporterResponse importDocument(Doc document) {



        //TODO ensure inited/destroyed only once when reusing importer




        initializeHandlersOnce();
        // Note: Doc reference, InputStream and metadata are all null-safe.

        //--- Document Handling ---
        try {
//            parseHandler.init(
//                    configuration.getParseConfig().getParseOptions());

            prepareDocumentForImporting(document);

            List<Doc> nestedDocs = new ArrayList<>();

            var response = executeHandlers(document, nestedDocs);
//            var filterStatus = doImportDocument(document, nestedDocs);
//            ImporterResponse response = null;
//            if (response.isRejected()) {
//                response = new ImporterResponse(
//                        document.getReference(), response);
//            } else {
//                response = new ImporterResponse(document);
//            }

            List<ImporterResponse> nestedResponses = new ArrayList<>();
            for (Doc childDoc : nestedDocs) {
                var nestedResponse = importDocument(childDoc);
                if (nestedResponse != null) {
                    nestedResponses.add(nestedResponse);
                }
            }
            response.setNestedResponses(nestedResponses);

            //--- Response Processor ---


            if (response.getParentResponse() == null
                    && !configuration.getResponseProcessors().isEmpty()) {
                processResponse(response);
            }
            return response;
        } catch (IOException | ImporterRuntimeException e) {
            LOG.warn("Could not import document: {}", document, e);
            return new ImporterResponse()
                    .setStatus(Status.ERROR)
                    .setDoc(document)
                    .setReference(document.getReference())
                    .setException(new ImporterException(
                            "Could not import document: " + document, e));
        } finally {
            destroyHandlersOnce();
        }
    }

    private synchronized void initializeHandlersOnce() {
        BeanUtil.visitAll(
                configuration.getHandler(),
                t -> {
                    try {
                        t.init();
                    } catch (IOException e) {
                        throw new ImporterRuntimeException(
                                "Coult not initialize handler: " + t, e);
                    }
                },
                DocumentHandler.class);
    }
    private synchronized void destroyHandlersOnce() {
        BeanUtil.visitAll(
                configuration.getHandler(),
                t -> {
                    try {
                        t.destroy();
                    } catch (IOException e) {
                        throw new ImporterRuntimeException(
                                "Coult not initialize handler: " + t, e);
                    }
                },
                DocumentHandler.class);
    }

    private void prepareDocumentForImporting(Doc document) {
        var docRecord = document.getDocRecord();

        //--- Ensure non-null content Type on Doc ---
        var ct = docRecord.getContentType();
        if (ct == null || StringUtils.isBlank(ct.toString())) {
            try {
                ct = ContentTypeDetector.detect(
                        document.getInputStream(), document.getReference());
            } catch (IOException e) {
                LOG.warn("Could not detect content type. Defaulting to "
                        + "\"application/octet-stream\".", e);
                ct = ContentType.valueOf("application/octet-stream");
            }
            docRecord.setContentType(ct);
        }

        //--- Try to detect content encoding if not already set ---

        try {
            var encoding = CharsetDetector.builder()
                    .priorityCharset(docRecord::getCharset)
                    .fallbackCharset((Charset) null)
                    .build()
                    .detect(document);
            docRecord.setCharset(encoding);
        } catch (IOException e) {
            LOG.debug("Problem detecting encoding for: {}",
                    docRecord.getReference(), e);
        }

        //--- Add basic metadata for what we know so far ---
        var meta = document.getMetadata();
        meta.set(DocMetadata.REFERENCE, document.getReference());
        meta.set(DocMetadata.CONTENT_TYPE, ct.toString());
        var contentFamily = ContentFamily.forContentType(ct);
        if (contentFamily != null) {
            meta.set(DocMetadata.CONTENT_FAMILY, contentFamily.toString());
        }
        if (docRecord.getCharset() != null) {
            meta.set(DocMetadata.CONTENT_ENCODING,
                    docRecord.getCharset().toString());
        }
    }

    // We deal with stream, but since only one of stream or file can be set,
    // convert file to stream only if set.
    private Doc toDocument(ImporterRequest req) throws ImporterException {
        ensureRequestStreamFactory();

        CachedInputStream is;
        var ref = StringUtils.trimToEmpty(req.getReference());
        if (req.getInputStream() != null) {
            // From input stream
            is = CachedInputStream.cache(
                    req.getInputStream(), requestStreamFactory);
        } else if (req.getFile() != null) {
            // From file
            if (!req.getFile().toFile().isFile()) {
                throw new ImporterException(
                        "File does not exists or is not a file: "
                                + req.getFile().toAbsolutePath());
            }
            try {
                is = requestStreamFactory.newInputStream(
                        new FileInputStream(req.getFile().toFile()));
            } catch (IOException e) {
                throw new ImporterException("Could not import file: "
                        + req.getFile().toAbsolutePath(), e);
            }
            if (StringUtils.isBlank(ref)) {
                ref = req.getFile().toFile().getAbsolutePath();
            }
        } else {
            is = requestStreamFactory.newInputStream();
        }

        var info = new DocRecord(ref);
        info.setCharset(req.getCharset());
        info.setContentType(req.getContentType());

        return new Doc(info, is, req.getMetadata());
    }

    private synchronized void ensureRequestStreamFactory() {
        if (requestStreamFactory != null) {
            return;
        }

        var tempDir = configuration.getTempDir();
        if (tempDir == null) {
            tempDir = Paths.get(ImporterConfig.DEFAULT_TEMP_DIR_PATH);
        }
        if (!tempDir.toFile().exists()) {
            try {
                Files.createDirectories(tempDir);
            } catch (IOException e) {
                throw new ImporterRuntimeException(
                        "Cannot create importer temporary directory: "
                                + tempDir, e);
            }
        }
        requestStreamFactory = new CachedStreamFactory(
                (int) configuration.getMaxMemoryPool(),
                (int) configuration.getMaxMemoryInstance(),
                configuration.getTempDir());
    }

//    private ImporterResponse doImportDocument(
//            Doc document, List<Doc> nestedDocs) throws IOException {
//
//        return executeHandlers(
//                document,
//                nestedDocs,
//                configuration.getHandler());
////                ,
//////                configuration.getPreParseConsumer(),
////                ParseState.PRE);
////
////        if (!filterStatus.isSuccess()) {
////            return filterStatus;
////        }
////        //--- Parse ---
////        //MAYBE: make parse just another handler in the chain?  Eliminating
////        //the need for pre and post handlers?
////        parseHandler.parseDocument(document, nestedDocs);
////
////        //--- Post-handlers ---
////        filterStatus = executeHandlers(
////                document,
////                nestedDocs,
////                configuration.getPostParseConsumer(),
////                ParseState.POST);
////        if (!filterStatus.isSuccess()) {
////            return filterStatus;
////        }
////        return PASSING_FILTER_STATUS;
//    }


    private void processResponse(ImporterResponse response) {
        for (ImporterResponseProcessor proc
                : configuration.getResponseProcessors()) {
            //MAYBE: do something with return response?
            proc.processImporterResponse(response);
        }
    }



    private ImporterResponse executeHandlers(
            Doc doc, List<Doc> childDocsHolder) throws ImporterException {

        var resp = new ImporterResponse()
                .setDoc(doc)
                .setReference(doc.getReference());

        if (configuration.getHandler() == null) {
            return resp.setStatus(Status.SUCCESS);
        }
        var ctx = DocContext.builder()
            .doc(doc)
            .eventManager(eventManager)
            .build();
        try {
            configuration.getHandler().accept(ctx);
        } catch (Exception e) {
            throw new DocumentHandlerException(e.getCause());
        } finally {
            try {
                ctx.flush();
            } catch (IOException e) {
                LOG.error("Could not flush document stream for {}",
                        ctx.reference(), e);
            }
        }
        childDocsHolder.addAll(ctx.childDocs());

        if (ctx.isRejected()) {
            return resp
                    .setStatus(Status.REJECTED)
                    .setRejectCause(ctx.rejectedBy())
                    .setDescription(Objects.toString(ctx.rejectedBy(), null));
        }
//        if (!ctx.getIncludeResolver().passes()) {
//            return new ImporterStatus(Status.REJECTED,
//                    "None of the filters with onMatch being INCLUDE got "
//                  + "matched.");
//        }
//        return PASSING_FILTER_STATUS;
        return resp.setStatus(Status.SUCCESS);
    }
}
