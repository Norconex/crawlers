/* Copyright 2022-2024 Norconex Inc.
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
package com.norconex.crawler.core;

import static com.norconex.commons.lang.ResourceLoader.getXmlString;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.input.NullInputStream;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.randomizers.misc.BooleanRandomizer;
import org.jeasy.random.randomizers.number.LongRandomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.crawler.MockCrawler;
import com.norconex.crawler.core.crawler.ReferencesProvider;
import com.norconex.crawler.core.fetch.MockFetcher;
import com.norconex.crawler.core.pipeline.committer.MockCommitterPipeline;
import com.norconex.crawler.core.pipeline.importer.MockImporterPipeline;
import com.norconex.crawler.core.pipeline.queue.MockQueuePipeline;
import com.norconex.crawler.core.processor.DocumentProcessor;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.core.session.CrawlSessionImpl;
import com.norconex.crawler.core.session.MockCrawlSession;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.core.store.MockDataStore;
import com.norconex.crawler.core.store.MockDataStoreEngine;
import com.norconex.importer.ImporterConfig;

import lombok.NonNull;

//TODO Replace the CoreStubber with this one so it always returns a session
// and we can get a subset if needed.
// Currently used with WithinCrawlSession. If it works great, use that
// annotation everywhere.
public final class CoreStubber2 {

    public static final String MOCK_CRAWLER_ID = "test-crawler";
    public static final String MOCK_CRAWL_SESSION_ID = "test-session";

    private static EasyRandom easyRandom = new EasyRandom(
            new EasyRandomParameters()
            .seed(System.currentTimeMillis())
            .collectionSizeRange(1, 5)
            .randomizationDepth(5)
            .scanClasspathForConcreteTypes(true)
            .overrideDefaultInitialization(true)
            .randomize(File.class,
                    () -> new File(new StringRandomizer(100).getRandomValue()))
            .randomize(Path.class,
                    () -> Path.of(new StringRandomizer(100).getRandomValue()))
            .randomize(Long.class,
                    () -> Math.abs(new LongRandomizer().getRandomValue()))
            .randomize(DataStoreEngine.class, MockDataStoreEngine::new)
            .randomize(DataStore.class, MockDataStore::new)
            .randomize(ImporterConfig.class, ImporterConfig::new)
            .randomize(UpsertRequest.class,
                    () -> new UpsertRequest(
                            new StringRandomizer(100).getRandomValue(),
                            new Properties(),
                            new NullInputStream()))
            .randomize(DeleteRequest.class,
                    () -> new DeleteRequest(
                            new StringRandomizer(100).getRandomValue(),
                            new Properties()))
            .randomize(Committer.class, MemoryCommitter::new)
            .randomize(Charset.class, () -> StandardCharsets.UTF_8)
            .randomize(SpoiledReferenceStrategizer.class,
                    GenericSpoiledReferenceStrategizer::new)
            .randomize(AtomicBoolean.class, () -> new AtomicBoolean(
                    new BooleanRandomizer().getRandomValue()))
            .excludeType(DocumentProcessor.class::equals)
            .excludeType(ReferencesProvider.class::equals)
    );

    private CoreStubber2() {}

    public static <T> T randomize(Class<T> cls) {
        return easyRandom.nextObject(cls);
    }

    //--- Crawl Session Config -------------------------------------------------

    /**
     * <p>Crawl session config stub:</p>
     * <ul>
     *   <li>Crawl session id: {@value #MOCK_CRAWL_SESSION_ID}.</li>
     *   <li>1 crawler from {@link #crawlerConfig()}.</li>
     *   <li>Default values for everything else.</li>
     * </ul>
     * @param workDir where to store generated files (including crawl store)
     * @return crawl session config
     */
    public static CrawlSessionConfig crawlSessionConfig(@NonNull Path workDir) {
        return new CrawlSessionConfig()
            .setWorkDir(workDir)
            .setId(MOCK_CRAWL_SESSION_ID)
            .setCrawlerConfigs(List.of(crawlerConfig()))
            .setClusterInformInterval(Duration.ofMillis(50))
            .setClusterInquireInterval(Duration.ofMillis(50));
    }
    /**
     * <p>Random crawl session config stub:</p>
     * <ul>
     *   <li>1 crawler from {@link #crawlerConfigRandom()}.</li>
     *   <li>Default values for everything else.</li>
     * </ul>
     *
     * @param workDir where to store generated files (including crawl store)
     * @return random crawl session config
     */
    public static CrawlSessionConfig crawlSessionConfigRandom(
            @NonNull Path workDir) {
        return easyRandom.nextObject(CrawlSessionConfig.class)
            .setWorkDir(workDir)
            .setCrawlerConfigs(List.of(crawlerConfigRandom()))
            .setClusterInformInterval(Duration.ofMillis(50))
            .setClusterInquireInterval(Duration.ofMillis(50));
    }

    //--- Crawl Session --------------------------------------------------------

    /**
     * A crawl session that has 1 crawler and 0 documents in queue.
     * @param workDir where to store generated files (including crawl store)
     * @return crawl session.
     */
    public static MockCrawlSession crawlSession(@NonNull Path workDir) {
        return crawlSession(workDir, null);
    }

    public static MockCrawlSession crawlSession(
            @NonNull Path workDir, CrawlSessionConfig sessionConfig) {

        var sessCfg = sessionConfig != null
                ? sessionConfig
                : crawlSessionConfig(workDir);
        sessCfg.setWorkDir(workDir); // just in case

        return new MockCrawlSession(
                CrawlSessionImpl
                .builder()
                .crawlerFactory((crawlSess, crawlerCfg) ->
                        new MockCrawler(crawlSess, crawlerCfg, crawlerImpl()))
                .crawlSessionConfig(sessCfg)
                .build()
        );
    }


    //--- Crawler Config -------------------------------------------------------

    /**
     * <p>Crawler config stub:</p>
     * <ul>
     *   <li>Crawler id: {@value #MOCK_CRAWLER_ID}.</li>
     *   <li>Single-threaded</li>
     *   <li>1 Memory Committer</li>
     *   <li>Default values for everything else.</li>
     * </ul>
     * @return crawler config
     */
    private static CrawlerConfig crawlerConfig() {
        var crawlerConfig = new CrawlerConfig();
        crawlerConfig.setId(MOCK_CRAWLER_ID);
        crawlerConfig.setNumThreads(1);
        crawlerConfig.setCommitters(List.of(new MemoryCommitter()));
        return crawlerConfig;
    }
    /**
     * <p>Random crawler config stub:</p>
     * <ul>
     *   <li>Single-threaded</li>
     *   <li>1 Memory Committer</li>
     *   <li>Random values for everything else.</li>
     * </ul>
     * @return random crawler config
     */
    private static CrawlerConfig crawlerConfigRandom() {
        var cfg = easyRandom.nextObject(CrawlerConfig.class);
        cfg.setNumThreads(1);
        cfg.setCommitters(List.of(new MemoryCommitter()));
        return cfg;
    }

    //--- CrawlerImpl ----------------------------------------------------------

    private static CrawlerImpl crawlerImpl() {

        var crawlerImplBuilder = CrawlerImpl.builder()
                .fetcherProvider(crawler -> new MockFetcher())
                .committerPipeline(new MockCommitterPipeline())
                .importerPipeline(new MockImporterPipeline())
//                .queueInitializer(new MockQueueInitializer(startReferences))
                .queuePipeline(new MockQueuePipeline());
        return crawlerImplBuilder.build();
    }


    //--- Misc. ----------------------------------------------------------------

    public static Path writeSampleConfigToDir(Path dir) {

        // if config file does not exist, assume we want to use the
        // stubber default.
        var cfgFile = dir.resolve("CoreStubber.xml");
        if (!Files.exists(cfgFile)) {
            try {
                Files.writeString(cfgFile, getXmlString(CoreStubber2.class));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return cfgFile;
    }
}
