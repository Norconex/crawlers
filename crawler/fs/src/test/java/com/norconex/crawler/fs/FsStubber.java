/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.crawler.fs;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.vfs2.FileSystemOptions;
import org.apache.commons.vfs2.impl.StandardFileSystemManager;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.misc.BooleanRandomizer;
import org.jeasy.random.randomizers.number.IntegerRandomizer;
import org.jeasy.random.randomizers.number.LongRandomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.ReferencesProvider;
import com.norconex.crawler.core.filter.OnMatch;
import com.norconex.crawler.core.filter.ReferenceFilter;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.core.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.fs.crawler.impl.FsCrawlerImplFactory;
import com.norconex.importer.ImporterConfig;

public final class FsStubber {

    public static final String MOCK_CRAWLER_ID = "test-crawler";
    public static final String MOCK_CRAWL_SESSION_ID = "test-session";
    public static final String MOCK_KEYSTORE_PATH =
            "src/test/resources/keystore.jks";
    public static final String MOCK_FS_PATH =
            "src/test/resources/mock-fs";

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
        .randomize(Integer.class,
                () -> Math.abs(new IntegerRandomizer().getRandomValue()))
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
        .randomize(SpoiledReferenceStrategizer.class,
                GenericSpoiledReferenceStrategizer::new)
        .randomize(AtomicBoolean.class, () -> new AtomicBoolean(
                new BooleanRandomizer().getRandomValue()))

        .excludeType(DataStoreEngine.class::equals)
        .excludeType(DataStore.class::equals)
        .excludeType(StandardFileSystemManager.class::equals)
        .excludeType(FileSystemOptions.class::equals)
        .excludeType(ReferencesProvider.class::equals)
        .excludeType(OnMatch.class::equals)
        .excludeType(ReferenceFilter.class::equals)
    );

    private FsStubber() {}

    public static <T> T randomize(Class<T> cls) {
        return easyRandom.nextObject(cls);
    }

    @SafeVarargs
    public static <T> Randomizer<T> randomizerOneOf(T... values) {
        return () -> randomOneOf(values);
    }

    @SafeVarargs
    public static <T> T randomOneOf(T... values) {
        if (ArrayUtils.isEmpty(values)) {
            return null;
        }
        return values[new Random().nextInt(values.length -1)];
    }

    //--- Crawl Session --------------------------------------------------------

    public static CrawlSession crawlSession(
            Path workDir, String... startPaths) {
        var sessionConfig = crawlSessionConfig(workDir);
        if (ArrayUtils.isNotEmpty(startPaths)) {
            FsTestUtil.getFirstCrawlerConfig(
                    sessionConfig).setStartReferences(List.of(startPaths));
        }
        return CrawlSession.builder()
            .crawlerFactory((crawlSess, crawlerCfg) ->
                Crawler.builder()
                    .crawlerConfig(crawlerCfg)
                    .crawlSession(crawlSess)
                    .crawlerImpl(FsCrawlerImplFactory.create())
                    .build()
            )
            .crawlSessionConfig(sessionConfig)
            .build();
    }


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
    public static CrawlSessionConfig crawlSessionConfig(Path workDir) {
        List<CrawlerConfig> crawlerConfigs = new ArrayList<>();
        crawlerConfigs.add(crawlerConfig());
        var sessionConfig = new CrawlSessionConfig();
        sessionConfig.setWorkDir(workDir);
        sessionConfig.setId(MOCK_CRAWL_SESSION_ID);
        sessionConfig.setCrawlerConfigs(crawlerConfigs);
        return sessionConfig;
    }

    /**
     * <p>Web crawler config stub:</p>
     * <ul>
     *   <li>Crawler id: {@value #MOCK_CRAWLER_ID}.</li>
     *   <li>1 thread.</li>
     *   <li>0 delay.</li>
     *   <li>1 MemoryCommitter.</li>
     * </ul>
     * @return crawler config
     */
    public static CrawlerConfig crawlerConfig() {
        var crawlerConfig = new CrawlerConfig();
        crawlerConfig.setId(MOCK_CRAWLER_ID);
        crawlerConfig.setNumThreads(1);
        crawlerConfig.setCommitters(List.of(new MemoryCommitter()));
        return crawlerConfig;
    }
}
