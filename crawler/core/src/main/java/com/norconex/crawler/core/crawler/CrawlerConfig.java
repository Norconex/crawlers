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
package com.norconex.crawler.core.crawler;

import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;

import lombok.Data;

/**
 * <p>
 * Base Crawler configuration. Crawlers usually read this configuration upon
 * starting up.  Once execution has started, it should not be changed
 * to avoid unexpected behaviors.
 * </p>
 *
 * <p>
 * Concrete implementations inherit the following XML configuration
 * options (typically within a <code>&lt;crawler&gt;</code> tag):
 * </p>
 *
 * {@nx.xml #init
 *
 *   <numThreads>(maximum number of threads)</numThreads>
 *   <maxDocuments>(maximum number of documents to crawl)</maxDocuments>
 *   <orphansStrategy>[PROCESS|IGNORE|DELETE]</orphansStrategy>
 *
 *   <stopOnExceptions>
 *     <!-- Repeatable -->
 *     <exception>(fully qualified class name of a an exception)</exception>
 *   </stopOnExceptions>
 *
 *   <eventListeners>
 *     <!-- Repeatable -->
 *     <listener class="(IEventListener implementation)"/>
 *   </eventListeners>
 *
 *   <dataStoreEngine class="(IDataStoreEngine implementation)" />
 * }
 *
 * {@nx.xml #pipeline-queue
 *   <referenceFilters>
 *     <!-- Repeatable -->
 *     <filter
 *         class="(IReferenceFilter implementation)"
 *         onMatch="[include|exclude]" />
 *   </referenceFilters>
 * }
 *
 * {@nx.xml #pipeline-import
 *   <metadataFilters>
 *     <!-- Repeatable -->
 *     <filter
 *         class="(IMetadataFilter implementation)"
 *         onMatch="[include|exclude]" />
 *   </metadataFilters>
 *
 *   <documentFilters>
 *     <!-- Repeatable -->
 *     <filter class="(IDocumentFilter implementation)" />
 *   </documentFilters>
 * }
 *
 * {@nx.xml #import
 *   <importer>
 *     <preParseHandlers>
 *       <!-- Repeatable -->
 *       <handler class="(an handler class from the Importer module)"/>
 *     </preParseHandlers>
 *     <documentParserFactory class="(IDocumentParser implementation)" />
 *     <postParseHandlers>
 *       <!-- Repeatable -->
 *       <handler class="(an handler class from the Importer module)"/>
 *     </postParseHandlers>
 *     <responseProcessors>
 *       <!-- Repeatable -->
 *       <responseProcessor
 *              class="(IImporterResponseProcessor implementation)" />
 *     </responseProcessors>
 *   </importer>
 * }
 *
 * {@nx.xml #checksum-meta
 *   <metadataChecksummer class="(IMetadataChecksummer implementation)" />
 * }
 *
 * {@nx.xml #dedup-meta
 *   <metadataDeduplicate>[false|true]</metadataDeduplicate>
 * }
 *
 * {@nx.xml #checksum-doc
 *   <documentChecksummer class="(IDocumentChecksummer implementation)" />
 * }
 *
 * {@nx.xml #dedup-doc
 *   <documentDeduplicate>[false|true]</documentDeduplicate>
 * }
 *
 * {@nx.xml #pipeline-committer
 *   <spoiledReferenceStrategizer
 *       class="(ISpoiledReferenceStrategizer implementation)" />
 *
 *   <committers>
 *     <committer class="(Committer implementation)" />
 *   </committers>
 * }
 *
 *
 */
@Data
public /* abstract */ class CrawlerConfig implements XMLConfigurable {

    private String id;


    @Override
    public void saveToXML(XML xml) {
        xml.setAttribute("id", id);
//        xml.addElement("numThreads", numThreads);
//        xml.addElement("maxDocuments", maxDocuments);
//        xml.addElementList("stopOnExceptions", "exception", stopOnExceptions);
//        xml.addElement("orphansStrategy", orphansStrategy);
//        xml.addElement("dataStoreEngine", dataStoreEngine);
//        xml.addElementList("referenceFilters", "filter", referenceFilters);
//        xml.addElementList("metadataFilters", "filter", metadataFilters);
//        xml.addElementList("documentFilters", "filter", documentFilters);
//        if (importerConfig != null) {
//            xml.addElement("importer", importerConfig);
//        }
//        xml.addElementList("committers", "committer", committers);
//        xml.addElement("metadataChecksummer", metadataChecksummer);
//        xml.addElement("metadataDeduplicate", metadataDeduplicate);
//        xml.addElement("documentChecksummer", documentChecksummer);
//        xml.addElement("documentDeduplicate", documentDeduplicate);
//        xml.addElement(
//                "spoiledReferenceStrategizer", spoiledReferenceStrategizer);
//
//        xml.addElementList("eventListeners", "listener", eventListeners);
//
//        saveCrawlerConfigToXML(xml);
    }
//    protected abstract void saveCrawlerConfigToXML(XML xml);

    @Override
    public final void loadFromXML(XML xml) {
//        xml.checkDeprecated("crawler/workDir", "collector/workDir", true);
//        xml.checkDeprecated("committer", "committers/committer", true);
//
        setId(xml.getString("@id", id));
//        setNumThreads(xml.getInteger("numThreads", numThreads));
//        setOrphansStrategy(xml.getEnum(
//                "orphansStrategy", OrphansStrategy.class, orphansStrategy));
//        setMaxDocuments(xml.getInteger("maxDocuments", maxDocuments));
//        setStopOnExceptions(xml.getClassList(
//                "stopOnExceptions/exception", stopOnExceptions));
//        setReferenceFilters(xml.getObjectListImpl(IReferenceFilter.class,
//                "referenceFilters/filter", referenceFilters));
//        setMetadataFilters(xml.getObjectListImpl(IMetadataFilter.class,
//                "metadataFilters/filter", metadataFilters));
//        setDocumentFilters(xml.getObjectListImpl(IDocumentFilter.class,
//                "documentFilters/filter", documentFilters));
//
//        XML importerXML = xml.getXML("importer");
//        if (importerXML != null) {
//            ImporterConfig cfg = new ImporterConfig();
//            importerXML.populate(cfg);
//            setImporterConfig(cfg);
//            //TODO handle ignore errors
//        } else if (getImporterConfig() == null) {
//            setImporterConfig(new ImporterConfig());
//        }
//
//        xml.checkDeprecated("crawlDataStoreEngine", "dataStoreEngine", true);
//        setDataStoreEngine(xml.getObjectImpl(
//                IDataStoreEngine.class, "dataStoreEngine", dataStoreEngine));
//        setCommitters(xml.getObjectListImpl(Committer.class,
//                "committers/committer", committers));
//        setMetadataChecksummer(xml.getObjectImpl(IMetadataChecksummer.class,
//                "metadataChecksummer", metadataChecksummer));
//        setMetadataDeduplicate(xml.getBoolean("metadataDeduplicate",
//                metadataDeduplicate));
//        setDocumentChecksummer(xml.getObjectImpl(IDocumentChecksummer.class,
//                "documentChecksummer", documentChecksummer));
//        setDocumentDeduplicate(xml.getBoolean("documentDeduplicate",
//                documentDeduplicate));
//        setSpoiledReferenceStrategizer(xml.getObjectImpl(
//                ISpoiledReferenceStrategizer.class,
//                "spoiledReferenceStrategizer", spoiledReferenceStrategizer));
//
//        xml.checkDeprecated("crawlerListeners", "eventListeners", true);
//        setEventListeners(xml.getObjectListImpl(IEventListener.class,
//                "eventListeners/listener", eventListeners));
//
//        loadCrawlerConfigFromXML(xml);
    }
}
