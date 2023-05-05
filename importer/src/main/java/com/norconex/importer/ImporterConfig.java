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

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.function.FunctionUtil;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.commons.lang.xml.flow.XMLFlow;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.HandlerContext;
import com.norconex.importer.handler.HandlerPredicate;
import com.norconex.importer.handler.ImporterHandler;
import com.norconex.importer.parser.ParseConfig;
import com.norconex.importer.response.ImporterResponseProcessor;

import lombok.Data;
import lombok.NonNull;

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
public class ImporterConfig implements XMLConfigurable {

    public static final String DEFAULT_TEMP_DIR_PATH =
            FileUtils.getTempDirectoryPath();
    /** 100 MB. */
    public static final long DEFAULT_MAX_STREAM_CACHE_POOL_SIZE =
            DataUnit.MB.toBytes(100).intValue();
    /** 1 GB. */
    public static final long DEFAULT_MAX_STREAM_CACHE_SIZE =
            DataUnit.GB.toBytes(1).intValue();



    private XMLFlow<HandlerContext> xmlFlow = new XMLFlow<>(
            HandlerConsumer.class, HandlerPredicate.class);

    private Consumer<HandlerContext> preParseConsumer;
    private Consumer<HandlerContext> postParseConsumer;

    private final List<ImporterResponseProcessor> responseProcessors =
            new ArrayList<>();

    private Path tempDir = Paths.get(DEFAULT_TEMP_DIR_PATH);
    private long maxMemoryInstance = DEFAULT_MAX_STREAM_CACHE_POOL_SIZE;
    private long maxMemoryPool = DEFAULT_MAX_STREAM_CACHE_SIZE;

    @NonNull
    private ParseConfig parseConfig = new ParseConfig();

    /**
     * Gets the {@link Consumer} to be executed on documents before
     * their parsing has occurred.
     * @return the document consumer
         */
    public Consumer<HandlerContext> getPreParseConsumer() {
        return preParseConsumer;
    }
    /**
     * <p>
     * Sets the {@link Consumer} to be executed on documents before
     * their parsing has occurred.  The consumer will automatically be
     * created when relying on XML configuration of handlers
     * ({@link ImporterHandler}). XML
     * configuration also offers extra XML tags to create basic "flow"
     * for handler execution.
     * </p>
     * <p>
     * To programmatically set multiple consumers or take advantage of the
     * many configurable {@link ImporterHandler} instances instead,
     * you can use {@link FunctionUtil#allConsumers(Consumer...)} or
     * {@link HandlerConsumer#fromHandlers(ImporterHandler...)}
     * respectively to create a consumer.
     * </p>
     * @param consumer the document consumer
         */
    public void setPreParseConsumer(Consumer<HandlerContext> consumer) {
        preParseConsumer = consumer;
    }
    /**
     * Gets the {@link Consumer} to be executed on documents after
     * their parsing has occurred.
     * @return the document consumer
         */
    public Consumer<HandlerContext> getPostParseConsumer() {
        return postParseConsumer;
    }
    /**
     * <p>
     * Sets the {@link Consumer} to be executed on documents after
     * their parsing has occurred.  The consumer will automatically be
     * created when relying on XML configuration of handlers
     * ({@link ImporterHandler}). XML
     * configuration also offers extra XML tags to create basic "flow"
     * for handler execution.
     * </p>
     * <p>
     * To programmatically set multiple consumers or take advantage of the
     * many configurable {@link ImporterHandler} instances instead,
     * you can use {@link FunctionUtil#allConsumers(Consumer...)} or
     * {@link HandlerConsumer#fromHandlers(ImporterHandler...)}
     * respectively to create a consumer.
     * </p>
     * @param consumer the document consumer
         */
    public void setPostParseConsumer(Consumer<HandlerContext> consumer) {
        postParseConsumer = consumer;
    }

    public List<ImporterResponseProcessor> getResponseProcessors() {
        return Collections.unmodifiableList(responseProcessors);
    }
    public void setResponseProcessors(
            List<ImporterResponseProcessor> responseProcessors) {
        CollectionUtil.setAll(this.responseProcessors, responseProcessors);
        CollectionUtil.removeNulls(this.responseProcessors);
    }

    /**
     * <p>
     * Gets the temporary directory where files can be deleted safely by the OS
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
     * @return path to temporary directory
     */
    public Path getTempDir() {
        return tempDir;
    }
    /**
     * <p>
     * Sets the temporary directory where files can be deleted safely by the OS
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
     */
    public void setTempDir(Path tempDir) {
        this.tempDir = tempDir;
    }

    /**
     * <p>
     * Gets the maximum number of bytes used for memory caching of a single
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
     * @return max document memory cache size
     */
    public long getMaxMemoryInstance() {
        return maxMemoryInstance;
    }
    /**
     * <p>
     * Sets the maximum number of bytes used for memory caching of a single
     * documents being processed.
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
     */
    public void setMaxMemoryInstance(long maxMemoryInstance) {
        this.maxMemoryInstance = maxMemoryInstance;
    }

    /**
     * <p>
     * Gets the maximum number of bytes used for memory caching of data for all
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
     * @return max documents memory pool cache size
     */
    public long getMaxMemoryPool() {
        return maxMemoryPool;
    }
    /**
     * <p>
     * Sets the maximum number of bytes used for memory caching of data for all
     * documents concurrently being processed.
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
     */
    public void setMaxMemoryPool(long maxMemoryPool) {
        this.maxMemoryPool = maxMemoryPool;
    }

    @Override
    public void loadFromXML(XML xml) {
        setTempDir(xml.getPath("tempDir", getTempDir()));
        setMaxMemoryInstance(
                xml.getDataSize("maxMemoryInstance", getMaxMemoryInstance()));
        setMaxMemoryPool(xml.getDataSize("maxMemoryPool", getMaxMemoryPool()));
        setPreParseConsumer(xmlFlow.parse(xml.getXML("preParseHandlers")));
        xml.ifXML("parse", parseConfig::loadFromXML);
        setPostParseConsumer(xmlFlow.parse(xml.getXML("postParseHandlers")));
        setResponseProcessors(xml.getObjectListImpl(
                ImporterResponseProcessor.class,
                "responseProcessors/responseProcessor",
                getResponseProcessors()));
    }

    @Override
    public void saveToXML(XML xml) {
        xml.addElement("tempDir", tempDir);
        xml.addElement("maxMemoryInstance", maxMemoryInstance);
        xml.addElement("maxMemoryPool", maxMemoryPool);

        xmlFlow.write(xml.addElement("preParseHandlers"), preParseConsumer);
        parseConfig.saveToXML(xml.addElement("parse"));
        xmlFlow.write(xml.addElement("postParseHandlers"), postParseConsumer);
        xml.addElementList(
                "responseProcessors", "responseProcessor", responseProcessors);
    }
}
