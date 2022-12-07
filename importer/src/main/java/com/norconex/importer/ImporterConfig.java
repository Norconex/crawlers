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
package com.norconex.importer;

import org.apache.commons.io.FileUtils;

import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;

/**
 * Importer configuration.
 */
public class ImporterConfig implements XMLConfigurable {

    public static final String DEFAULT_TEMP_DIR_PATH =
            FileUtils.getTempDirectoryPath();
    /** 100 MB. */
    public static final long DEFAULT_MAX_STREAM_CACHE_POOL_SIZE =
            DataUnit.MB.toBytes(100).intValue();
    /** 1 GB. */
    public static final long DEFAULT_MAX_STREAM_CACHE_SIZE =
            DataUnit.GB.toBytes(1).intValue();

//    private IDocumentParserFactory documentParserFactory =
//            new GenericDocumentParserFactory();
//
//    private XMLFlow<HandlerContext> xmlFlow = new XMLFlow<>(
//            HandlerConsumer.class, HandlerPredicate.class);
//
//    private Consumer<HandlerContext> preParseConsumer;
//    private Consumer<HandlerContext> postParseConsumer;
//
//    private final List<IImporterResponseProcessor> responseProcessors =
//            new ArrayList<>();
//
//    private Path tempDir = Paths.get(DEFAULT_TEMP_DIR_PATH);
//    private long maxMemoryInstance = DEFAULT_MAX_STREAM_CACHE_POOL_SIZE;
//    private long maxMemoryPool = DEFAULT_MAX_STREAM_CACHE_SIZE;
//    private Path parseErrorsSaveDir;
//
//    public IDocumentParserFactory getParserFactory() {
//        return documentParserFactory;
//    }
//    public void setParserFactory(IDocumentParserFactory parserFactory) {
//        documentParserFactory = parserFactory;
//    }
//
//    /**
//     * Gets the directory where file generating parsing errors will be saved.
//     * Default is <code>null</code> (not storing errors).
//     * @return directory where to save error files
//     */
//    public Path getParseErrorsSaveDir() {
//        return parseErrorsSaveDir;
//    }
//    /**
//     * Sets the directory where file generating parsing errors will be saved.
//     * @param parseErrorsSaveDir directory where to save error files
//     */
//    public void setParseErrorsSaveDir(Path parseErrorsSaveDir) {
//        this.parseErrorsSaveDir = parseErrorsSaveDir;
//    }
//
//    /**
//     * Gets the {@link Consumer} to be executed on documents before
//     * their parsing has occurred.
//     * @return the document consumer
//     * @since 3.0.0
//     */
//    public Consumer<HandlerContext> getPreParseConsumer() {
//        return preParseConsumer;
//    }
//    /**
//     * <p>
//     * Sets the {@link Consumer} to be executed on documents before
//     * their parsing has occurred.  The consumer will automatically be
//     * created when relying on XML configuration of handlers
//     * ({@link IImporterHandler}). XML
//     * configuration also offers extra XML tags to create basic "flow"
//     * for handler execution.
//     * </p>
//     * <p>
//     * To programmatically set multiple consumers or take advantage of the
//     * many configurable {@link IImporterHandler} instances instead,
//     * you can use {@link FunctionUtil#allConsumers(Consumer...)} or
//     * {@link HandlerConsumer#fromHandlers(IImporterHandler...)}
//     * respectively to create a consumer.
//     * </p>
//     * @param consumer the document consumer
//     * @since 3.0.0
//     */
//    public void setPreParseConsumer(Consumer<HandlerContext> consumer) {
//        preParseConsumer = consumer;
//    }
//    /**
//     * Gets the {@link Consumer} to be executed on documents after
//     * their parsing has occurred.
//     * @return the document consumer
//     * @since 3.0.0
//     */
//    public Consumer<HandlerContext> getPostParseConsumer() {
//        return postParseConsumer;
//    }
//    /**
//     * <p>
//     * Sets the {@link Consumer} to be executed on documents after
//     * their parsing has occurred.  The consumer will automatically be
//     * created when relying on XML configuration of handlers
//     * ({@link IImporterHandler}). XML
//     * configuration also offers extra XML tags to create basic "flow"
//     * for handler execution.
//     * </p>
//     * <p>
//     * To programmatically set multiple consumers or take advantage of the
//     * many configurable {@link IImporterHandler} instances instead,
//     * you can use {@link FunctionUtil#allConsumers(Consumer...)} or
//     * {@link HandlerConsumer#fromHandlers(IImporterHandler...)}
//     * respectively to create a consumer.
//     * </p>
//     * @param consumer the document consumer
//     * @since 3.0.0
//     */
//    public void setPostParseConsumer(Consumer<HandlerContext> consumer) {
//        postParseConsumer = consumer;
//    }
//
////    /**
////     * Gets importer handlers to be executed on documents before they are
////     * parsed.
////     * @return list of importer handlers
////     * @deprecated Since 3.0.0, use {@link #getPreParseConsumer()} instead
////     */
////    @Deprecated
////    public List<IImporterHandler> getPreParseHandlers() {
////        List<IImporterHandler> handlers = new ArrayList<>();
////        BeanUtil.visitAll(
////                preParseConsumer, handlers::add, IImporterHandler.class);
////        return Collections.unmodifiableList(handlers);
////    }
////    /**
////     * Sets importer handlers to be executed on documents before they are
////     * parsed.
////     * @param preParseHandlers list of importer handlers
////     * @deprecated Since 3.0.0, use {@link #setPreParseConsumer(Consumer)}
////     * instead
////     */
////    @Deprecated
////    public void setPreParseHandlers(List<IImporterHandler> preParseHandlers) {
////        setPreParseConsumer(HandlerConsumer.fromHandlers(preParseHandlers));
////    }
////    /**
////     * Gets importer handlers to be executed on documents after they are
////     * parsed.
////     * @return list of importer handlers
////     * @deprecated Since 3.0.0, use {@link #getPostParseConsumer()} instead
////     */
////    @Deprecated
////    public List<IImporterHandler> getPostParseHandlers() {
////        List<IImporterHandler> handlers = new ArrayList<>();
////        BeanUtil.visitAll(
////                postParseConsumer, handlers::add, IImporterHandler.class);
////        return Collections.unmodifiableList(handlers);
////    }
////    /**
////     * Sets importer handlers to be executed on documents after they are
////     * parsed.
////     * @param postParseHandlers list of importer handlers
////     * @deprecated Since 3.0.0, use {@link #setPostParseConsumer(Consumer)}
////     * instead
////     */
////    @Deprecated
////    public void setPostParseHandlers(List<IImporterHandler> postParseHandlers) {
////        setPostParseConsumer(HandlerConsumer.fromHandlers(postParseHandlers));
////    }
//
//    public List<IImporterResponseProcessor> getResponseProcessors() {
//        return Collections.unmodifiableList(responseProcessors);
//    }
//    public void setResponseProcessors(
//            List<IImporterResponseProcessor> responseProcessors) {
//        CollectionUtil.setAll(this.responseProcessors, responseProcessors);
//    }
//
//    /**
//     * <p>
//     * Gets the temporary directory where files can be deleted safely by the OS
//     * or any other processes when the Importer is not running.
//     * When not set, the importer will use the system temporary directory.
//     * </p>
//     * <p>
//     * This only get used when the Importer launched directly from the
//     * command-line or when importing documents via
//     * {@link Importer#importDocument(ImporterRequest)}.  Documents
//     * imported via {@link Importer#importDocument(Doc)} already have
//     * their temp/cache directory built-in.
//     * </p>
//     * @return path to temporary directory
//     */
//    public Path getTempDir() {
//        return tempDir;
//    }
//    /**
//     * <p>
//     * Sets the temporary directory where files can be deleted safely by the OS
//     * or any other processes when the Importer is not running.
//     * When not set, the importer will use the system temporary directory.
//     * </p>
//     * <p>
//     * This only get used when the Importer launched directly from the
//     * command-line or when importing documents via
//     * {@link Importer#importDocument(ImporterRequest)}.  Documents
//     * imported via {@link Importer#importDocument(Doc)} already have
//     * their temp/cache directory built-in.
//     * </p>
//     * @param tempDir path to temporary directory
//     */
//    public void setTempDir(Path tempDir) {
//        this.tempDir = tempDir;
//    }
//
//    /**
//     * <p>
//     * Gets the maximum number of bytes used for memory caching of a single
//     * documents being processed. Default
//     * is {@link #DEFAULT_MAX_STREAM_CACHE_POOL_SIZE}.
//     * </p>
//     * <p>
//     * This only get used when the Importer launched directly from the
//     * command-line or when importing documents via
//     * {@link Importer#importDocument(ImporterRequest)}.  Documents
//     * imported via {@link Importer#importDocument(Doc)} already have
//     * their memory settings built-in.
//     * </p>
//     * @return max document memory cache size
//     * @since 3.0.0
//     */
//    public long getMaxMemoryInstance() {
//        return maxMemoryInstance;
//    }
//    /**
//     * <p>
//     * Sets the maximum number of bytes used for memory caching of a single
//     * documents being processed.
//     * </p>
//     * <p>
//     * This only get used when the Importer launched directly from the
//     * command-line or when importing documents via
//     * {@link Importer#importDocument(ImporterRequest)}.  Documents
//     * imported via {@link Importer#importDocument(Doc)} already have
//     * their memory settings built-in.
//     * </p>
//     * @param maxMemoryInstance max document memory cache size
//     * @since 3.0.0
//     */
//    public void setMaxMemoryInstance(long maxMemoryInstance) {
//        this.maxMemoryInstance = maxMemoryInstance;
//    }
//
//    /**
//     * <p>
//     * Gets the maximum number of bytes used for memory caching of data for all
//     * documents concurrently being processed. Default
//     * is {@link #DEFAULT_MAX_STREAM_CACHE_SIZE}.
//     * </p>
//     * <p>
//     * This only get used when the Importer launched directly from the
//     * command-line or when importing documents via
//     * {@link Importer#importDocument(ImporterRequest)}.  Documents
//     * imported via {@link Importer#importDocument(Doc)} already have
//     * their memory settings built-in.
//     * </p>
//     * @return max documents memory pool cache size
//     * @since 3.0.0
//     */
//    public long getMaxMemoryPool() {
//        return maxMemoryPool;
//    }
//    /**
//     * <p>
//     * Sets the maximum number of bytes used for memory caching of data for all
//     * documents concurrently being processed.
//     * </p>
//     * <p>
//     * This only get used when the Importer launched directly from the
//     * command-line or when importing documents via
//     * {@link Importer#importDocument(ImporterRequest)}.  Documents
//     * imported via {@link Importer#importDocument(Doc)} already have
//     * their memory settings built-in.
//     * </p>
//     * @param maxMemoryPool max documents memory pool cache size
//     * @since 3.0.0
//     */
//    public void setMaxMemoryPool(long maxMemoryPool) {
//        this.maxMemoryPool = maxMemoryPool;
//    }
//
////    /**
////     * @deprecated Since 3.0.0, use {@link #getMaxMemoryInstance()}.
////     * @return byte amount
////     */
////    @Deprecated
////    public long getMaxFileCacheSize() {
////        return maxMemoryInstance;
////    }
////    /**
////     * @deprecated Since 3.0.0, use {@link #setMaxMemoryInstance(long)}.
////     * @param maxFileCacheSize byte amount
////     */
////    @Deprecated
////    public void setMaxFileCacheSize(long maxFileCacheSize) {
////        maxMemoryInstance = maxFileCacheSize;
////    }
////    /**
////     * @deprecated Since 3.0.0, use {@link #getMaxMemoryPool()}.
////     * @return byte amount
////     */
////    @Deprecated
////    public long getMaxFilePoolCacheSize() {
////        return maxMemoryPool;
////    }
////    /**
////     * @deprecated Since 3.0.0, use {@link #setMaxMemoryPool(long)}.
////     * @param maxFilePoolCacheSize byte amount
////     */
////    @Deprecated
////    public void setMaxFilePoolCacheSize(long maxFilePoolCacheSize) {
////        maxMemoryPool = maxFilePoolCacheSize;
////    }
    @Override
    public void loadFromXML(XML xml) {
//        setTempDir(xml.getPath("tempDir", getTempDir()));
//        setParseErrorsSaveDir(
//                xml.getPath("parseErrorsSaveDir", getParseErrorsSaveDir()));
//
//        xml.checkDeprecated("maxFileCacheSize", "maxMemoryInstance", true);
//        setMaxMemoryInstance(
//                xml.getDataSize("maxMemoryInstance", getMaxMemoryInstance()));
//
//        xml.checkDeprecated("maxFilePoolCacheSize", "maxMemoryPool", true);
//        setMaxMemoryPool(xml.getDataSize("maxMemoryPool", getMaxMemoryPool()));
//
//        setPreParseConsumer(xmlFlow.parse(xml.getXML("preParseHandlers")));
//        setParserFactory(xml.getObjectImpl(IDocumentParserFactory.class,
//                "documentParserFactory", getParserFactory()));
//        setPostParseConsumer(xmlFlow.parse(xml.getXML("postParseHandlers")));
//        setResponseProcessors(xml.getObjectListImpl(
//                IImporterResponseProcessor.class,
//                "responseProcessors/responseProcessor",
//                getResponseProcessors()));
    }
//
    @Override
    public void saveToXML(XML xml) {
//        xml.addElement("tempDir", tempDir);
//        xml.addElement("parseErrorsSaveDir", parseErrorsSaveDir);
//        xml.addElement("maxMemoryInstance", maxMemoryInstance);
//        xml.addElement("maxMemoryPool", maxMemoryPool);
//
//        xmlFlow.write(xml.addElement("preParseHandlers"), preParseConsumer);
//        xml.addElement("documentParserFactory", documentParserFactory);
//        xmlFlow.write(xml.addElement("postParseHandlers"), postParseConsumer);
//        xml.addElementList(
//                "responseProcessors", "responseProcessor", responseProcessors);
    }
//
//    @Override
//    public boolean equals(final Object other) {
//        return EqualsBuilder.reflectionEquals(this, other);
//    }
//    @Override
//    public int hashCode() {
//        return HashCodeBuilder.reflectionHashCode(this);
//    }
//    @Override
//    public String toString() {
//        return new ReflectionToStringBuilder(
//                this, ToStringStyle.SHORT_PREFIX_STYLE).toString();
//    }
}
