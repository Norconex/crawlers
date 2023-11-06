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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.flow.JsonFlow;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.importer.handler.DocContext;
import com.norconex.importer.handler.parser.impl.DefaultParser;
import com.norconex.importer.response.ImporterResponseProcessor;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Importer configuration. Refer to {@link ParseConfig} for parse-specific
 * configuration documentation.
 *
 * {@nx.xml.usage
 * <importer>
 *   <maxMemoryPool>
 *     (total memory shared by multiple importer instances in the same JVM)
 *   </maxMemoryPool>
 *   <maxMemoryInstance>
 *     (total memory allocated for processing individual files, before
 *      they are persisted to disk, to avoid memory issues)
 *   </maxMemoryInstance>
 *   <tempDir>(Optionally overwrite the default temp directory)</tempDir>
 *   <preParseHandlers>
 *     (any combination of taggers, transformers, splitters, filters,
 *      and XML conditions)
 *   </preParseHandlers>
 *
 *   {@nx.include com.norconex.importer.parser.ParseConfig@nx.xml.usage}
 *
 *   <postParseHandlers>
 *     (any combination of taggers, transformers, splitters, filters,
 *      and XML conditions)
 *   </postParseHandlers>
 *   <responseProcessors>
 *     <responseProcessor class="...">
 *       (optional class that can manipulate the importer response)
 *     </responseProcessor>
 *   </responseProcessors>
 * </importer>
 * }
 *
 * @see ParseConfig
 */
@SuppressWarnings("javadoc")
@Data
@Accessors(chain = true)
public class ImporterConfig {

    public static final String DEFAULT_TEMP_DIR_PATH =
            FileUtils.getTempDirectoryPath();
    /** 100 MB. */
    public static final long DEFAULT_MAX_STREAM_CACHE_POOL_SIZE =
            DataUnit.MB.toBytes(100).intValue();
    /** 1 GB. */
    public static final long DEFAULT_MAX_STREAM_CACHE_SIZE =
            DataUnit.GB.toBytes(1).intValue();


    @JsonFlow(builder = ImporterFlowConfigBuilder.class)
    @JsonProperty("handlers")

//TODO make a list, even if one entry can be Consumers
    private Consumer<DocContext> handler = new DefaultParser();


//    /**
//     * <p>
//     * The {@link Consumer} to be executed on documents before
//     * their parsing has occurred. While not mandatory, it is strongly
//     * recommended you consumer implements {@link BaseDocumentHandler}
//     * or is decorated with either
//     * {@link BaseDocumentHandler#decorate(Consumer)} or
//     * {@link BaseDocumentHandler#decorate(FailableConsumer)}
//     * </p>
//     * <p>
//     * Be careful that your pre-par consumers can deal with the expected
//     * document types. To consume a document once its plain text has been
//     * extracted, use {@link #postParseConsumer} instead.
//     * </p>
//     * <p>
//     * To programmatically set multiple consumers or take advantage of the
//     * handlers defined by the Importer, you can use {@link Consumers}
//     * to wrap them.
//     * </p>
//     * @param consumer the pre-parse document consumer
//     * @return pre-parse document consumer
//     */
//    @JsonFlow
//    private Consumer<DocContext> preParseConsumer;
//
//    /**
//     * <p>
//     * The {@link Consumer} to be executed on documents after
//     * their parsing has occurred.
//     * </p>
//     * <p>
//     * <p>
//     * To programmatically set multiple consumers or take advantage of the
//     * handlers defined by the Importer, you can use {@link Consumers}
//     * to wrap them.
//     * </p>
//     * @param consumer the document consumer
//     */
//    @JsonFlow
//    private Consumer<DocContext> postParseConsumer;

    /**
     * Processors of importer response. Invoked when a document has
     * been fully imported.
     */
    private final List<ImporterResponseProcessor> responseProcessors =
            new ArrayList<>();

    /**
     * <p>
     * The temporary directory where files can be deleted safely by the OS
     * or any other processes when the Importer is not running.
     * When not set, the importer will use the system temporary directory.
     * </p>
     * <p>
     * This only get used when the Importer launched directly from the
     * command-line or when importing documents via
     * {@link Importer#importDocument(ImporterRequest)}.  Documents
     * imported via
     * {@link Importer#importDocument(com.norconex.importer.doc.Doc)} already
     * have their temp/cache directory built-in.
     * </p>
     * @param tempDir path to temporary directory
     * @return path to temporary directory
     */
    private Path tempDir = Paths.get(DEFAULT_TEMP_DIR_PATH);

    /**
     * <p>
     * The maximum number of bytes used for memory caching of a single
     * documents being processed. Default
     * is {@link #DEFAULT_MAX_STREAM_CACHE_POOL_SIZE}.
     * </p>
     * <p>
     * This only get used when the Importer launched directly from the
     * command-line or when importing documents via
     * {@link Importer#importDocument(ImporterRequest)}.  Documents
     * imported via
     * {@link Importer#importDocument(com.norconex.importer.doc.Doc)} already
     * have their temp/cache directory built-in.
     * </p>
     * @param maxMemoryInstance max document memory cache size
     * @return max document memory cache size
     */
    private long maxMemoryInstance = DEFAULT_MAX_STREAM_CACHE_POOL_SIZE;

    /**
     * <p>
     * The maximum number of bytes used for memory caching of data for all
     * documents concurrently being processed. Default
     * is {@link #DEFAULT_MAX_STREAM_CACHE_SIZE}.
     * </p>
     * <p>
     * This only get used when the Importer launched directly from the
     * command-line or when importing documents via
     * {@link Importer#importDocument(ImporterRequest)}.  Documents
     * imported via
     * {@link Importer#importDocument(com.norconex.importer.doc.Doc)} already
     * have their temp/cache directory built-in.
     * </p>
     * @param maxMemoryPool max documents memory pool cache size
     * @return max documents memory pool cache size
     */
    private long maxMemoryPool = DEFAULT_MAX_STREAM_CACHE_SIZE;

    public List<ImporterResponseProcessor> getResponseProcessors() {
        return Collections.unmodifiableList(responseProcessors);
    }
    public ImporterConfig setResponseProcessors(
            List<ImporterResponseProcessor> responseProcessors) {
        CollectionUtil.setAll(this.responseProcessors, responseProcessors);
        CollectionUtil.removeNulls(this.responseProcessors);
        return this;
    }
}
