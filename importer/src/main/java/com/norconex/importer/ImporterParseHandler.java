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
package com.norconex.importer;

import static com.norconex.importer.ImporterEvent.IMPORTER_PARSER_BEGIN;
import static com.norconex.importer.ImporterEvent.IMPORTER_PARSER_END;
import static com.norconex.importer.ImporterEvent.IMPORTER_PARSER_ERROR;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.file.ContentType;
import com.norconex.importer.ImporterEvent.ImporterEventBuilder;
import com.norconex.importer.doc.Doc;
import com.norconex.importer.doc.DocMetadata;
import com.norconex.importer.parser.DocumentParser;
import com.norconex.importer.parser.DocumentParserException;
import com.norconex.importer.parser.ParseConfig;
import com.norconex.importer.parser.ParseOptions;
import com.norconex.importer.parser.ParseState;

import lombok.extern.slf4j.Slf4j;

//TODO Maybe make public, move to "parser" package and rename?
@Slf4j
class ImporterParseHandler {

    private final EventManager eventManager;
    private final ParseConfig parseConfig;
    private boolean initialized;

    public ImporterParseHandler(Importer importer) {
        eventManager = importer.getEventManager();
        parseConfig = importer.getImporterConfig().getParseConfig();
    }

    // We make sure we are initializing only once, in case an Importer
    // instance is reused.
    synchronized void init(ParseOptions parseOptions)
            throws DocumentParserException {
        if (initialized) {
            return;
        }
        parseConfig.getDefaultParser().init(parseOptions);
        for (DocumentParser parser : parseConfig.getParsers().values()) {
            parser.init(parseOptions);
        }

        //TODO, set XFDL

        initialized = true;
    }

    // returns whether parsing occurred
    boolean parseDocument(
            final Doc doc,
            final List<Doc> embeddedDocs)
                    throws IOException, ImporterException {

        // Do not attempt to parse zero-length content
        if (doc.getInputStream().isEmpty()) {
            LOG.debug("No content for \"{}\".", doc.getReference());
            return false;
        }

        // do not parse if not matching includes/excludes
        var contentType =
                doc.getDocRecord().getContentType().toBaseTypeString();
        if ((!parseConfig.getContentTypeIncludes().isEmpty()
                && parseConfig.getContentTypeIncludes().stream()
                    .noneMatch(tm -> tm.test(contentType)))
                || parseConfig.getContentTypeExcludes().stream()
                    .anyMatch(tm -> tm.test(contentType))) {
            return false;
        }

        var parser = parseConfig.getParserOrDefault(
                doc.getDocRecord().getContentType());

        // No parser means no parsing, so we simply return
        if (parser == null) {
            LOG.debug("No parser for \"{}\"", doc.getReference());
            return false;
        }

        fire(IMPORTER_PARSER_BEGIN, doc,
                b -> b.source(parser).parseState(ParseState.PRE));

        try (var out = doc.getStreamFactory().newOuputStream();
             var output = new OutputStreamWriter(out, UTF_8)) {

            LOG.debug("Parser \"{}\" about to parse \"{}\".",
                    parser.getClass().getCanonicalName(),
                    doc.getReference());
            var nestedDocs = parser.parseDocument(doc, output);
            output.flush();
            if (doc.getDocRecord().getContentType() == null) {
                doc.getDocRecord().setContentType(ContentType.valueOf(
                        StringUtils.trimToNull(doc.getMetadata().getString(
                                DocMetadata.CONTENT_TYPE))));
            }
            if (StringUtils.isBlank(doc.getDocRecord().getCharset())) {
                doc.getDocRecord().setCharset(
                        doc.getMetadata().getString(
                                DocMetadata.CONTENT_ENCODING));
            }
            if (CollectionUtils.isNotEmpty(nestedDocs)) {
                for (var i = 0; i < nestedDocs.size() ; i++) {
                    var meta = nestedDocs.get(i).getMetadata();
                    meta.add(DocMetadata.EMBEDDED_INDEX, i);
                    meta.add(DocMetadata.EMBEDDED_PARENT_REFERENCES,
                            doc.getReference());
                }
                embeddedDocs.addAll(nestedDocs);
            }
            fire(IMPORTER_PARSER_END, doc,
                    b -> b.source(parser).parseState(ParseState.POST));

            if (out.isCacheEmpty()) {
                LOG.debug("Parser \"{}\" did not produce new content for: {}",
                        parser.getClass(), doc.getReference());
                doc.setInputStream(doc.getStreamFactory().newInputStream());
            } else {
                var newInputStream = out.getInputStream();
                doc.setInputStream(newInputStream);
            }
            return true;
        } catch (DocumentParserException e) {
            fire(IMPORTER_PARSER_ERROR, doc, b -> b
                    .source(parser).parseState(ParseState.PRE).exception(e));
            if (parseConfig.getErrorsSaveDir() != null) {
                saveParseError(doc, e);
            }
            throw e;
        }
    }

    private void saveParseError(Doc doc, Exception e) {
        var saveDir = parseConfig.getErrorsSaveDir();
        if (!saveDir.toFile().exists()) {
            try {
                Files.createDirectories(saveDir);
            } catch (IOException ex) {
                LOG.error("Cannot create importer temporary directory: "
                        + saveDir, ex);
            }
        }

        var uuid = UUID.randomUUID().toString();

        // Save exception
        try (var exWriter = new PrintWriter(Files.newBufferedWriter(
                saveDir.resolve(uuid + "-error.txt")))) {
            e.printStackTrace(exWriter);
        } catch (IOException e1) {
            LOG.error("Cannot save parse exception.", e1);
        }

        if (doc == null) {
            LOG.error("""
                The importer document that cause a parse error is\s\
                null. It is not possible to save it.  Only the\s\
                exception will be saved.""");
            return;
        }

        // Save metadata
        try (var metaWriter = new PrintWriter(Files.newBufferedWriter(
                saveDir.resolve(uuid + "-meta.txt")))) {
            doc.getMetadata().storeToProperties(metaWriter);
        } catch (IOException e1) {
            LOG.error("Cannot save parse error file metadata.", e1);
        }

        // Save content
        try {
            var ext = FilenameUtils.getExtension(doc.getReference());
            if (StringUtils.isBlank(ext)) {
                var ct = doc.getDocRecord().getContentType();
                if (ct != null) {
                    ext = ct.getExtension();
                }
            }
            if (StringUtils.isBlank(ext)) {
                ext = "unknown";
            }

            Files.copy(doc.getInputStream(),
                    saveDir.resolve(uuid + "-content." + ext),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e1) {
            LOG.error("Cannot save parse error file content.", e1);
        }
    }

    private void fire(
            String eventName, Doc doc, Consumer<ImporterEventBuilder<?, ?>> c) {
        var b = ImporterEvent.builder()
                .name(eventName)
                .document(doc);
        if (c != null) {
            c.accept(b);
        }
        eventManager.fire(b.build());
    }
}
