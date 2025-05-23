/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.stubs;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.apache.commons.io.input.NullInputStream;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.misc.BooleanRandomizer;
import org.jeasy.random.randomizers.number.LongRandomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.doc.operations.DocumentConsumer;
import com.norconex.crawler.core.doc.operations.checksum.DocumentChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.impl.GenericMetadataChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.impl.Md5DocumentChecksummer;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.operations.filter.MetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.doc.operations.filter.impl.ExtensionReferenceFilter;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericMetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.operations.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.pipelines.queue.ReferencesProvider;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.mocks.grid.MockFailingGrid;
import com.norconex.crawler.core.mocks.grid.MockFailingGridConnector;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;
import com.norconex.importer.ImporterConfig;

import lombok.NonNull;

public final class StubCrawlerConfig {

    public static final String CRAWLER_ID = "test-crawler";

    public static final EasyRandom RANDOMIZER = new EasyRandom(
            new EasyRandomParameters()
                    .seed(System.currentTimeMillis())
                    .collectionSizeRange(1, 5)
                    .randomizationDepth(5)
                    .scanClasspathForConcreteTypes(false)
                    .overrideDefaultInitialization(true)
                    .randomize(
                            File.class,
                            () -> new File(
                                    new StringRandomizer(100).getRandomValue()))
                    .randomize(
                            Path.class,
                            () -> Path.of(
                                    new StringRandomizer(100).getRandomValue()))
                    .randomize(
                            Long.class,
                            () -> Math
                                    .abs(new LongRandomizer().getRandomValue()))
                    .randomize(Grid.class, MockFailingGrid::new)
                    .randomize(GridConnector.class,
                            MockFailingGridConnector::new)
                    .randomize(ImporterConfig.class, ImporterConfig::new)
                    .randomize(
                            UpsertRequest.class,
                            () -> new UpsertRequest(
                                    new StringRandomizer(100).getRandomValue(),
                                    new Properties(),
                                    new NullInputStream()))
                    .randomize(
                            DeleteRequest.class,
                            () -> new DeleteRequest(
                                    new StringRandomizer(100).getRandomValue(),
                                    new Properties()))
                    .randomize(Committer.class, MemoryCommitter::new)
                    .randomize(Charset.class, () -> StandardCharsets.UTF_8)
                    .randomize(
                            SpoiledReferenceStrategizer.class,
                            GenericSpoiledReferenceStrategizer::new)
                    .randomize(
                            AtomicBoolean.class, () -> new AtomicBoolean(
                                    new BooleanRandomizer().getRandomValue()))
                    .randomize(
                            ReferenceFilter.class,
                            randomInstanceOf(
                                    ExtensionReferenceFilter.class,
                                    GenericReferenceFilter.class))
                    .randomize(
                            MetadataFilter.class,
                            randomInstanceOf(
                                    ExtensionReferenceFilter.class,
                                    GenericReferenceFilter.class,
                                    GenericMetadataFilter.class))
                    .randomize(
                            DocumentFilter.class,
                            randomInstanceOf(
                                    ExtensionReferenceFilter.class,
                                    GenericReferenceFilter.class,
                                    GenericMetadataFilter.class))
                    .randomize(
                            MetadataChecksummer.class,
                            GenericMetadataChecksummer::new)
                    .randomize(
                            DocumentChecksummer.class,
                            Md5DocumentChecksummer::new)
                    .excludeType(DocumentConsumer.class::equals)
                    .excludeType(ReferencesProvider.class::equals)
                    .excludeType(Fetcher.class::equals));

    private StubCrawlerConfig() {
    }

    public static CrawlConfig memoryCrawlerConfig(Path workDir) {
        return toMemoryCrawlerConfig(workDir, new CrawlConfig());
    }

    public static CrawlConfig memoryCrawlerConfig(
            Path workDir, Class<? extends CrawlConfig> cfgClass) {
        return toMemoryCrawlerConfig(workDir, ClassUtil.newInstance(cfgClass));
    }

    /**
     * Takes an existing config and make it a "memory" config.
     * @param workDir test working directory
     * @param cfg crawler config
     * @return same config instance, for chaining
     */
    public static CrawlConfig toMemoryCrawlerConfig(
            Path workDir, CrawlConfig cfg) {
        return cfg.setId(CRAWLER_ID)
                // Some tests define this so we can't set it as default here.
                //                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()));
    }

    public static CrawlConfig randomMemoryCrawlerConfig(Path workDir) {
        return randomMemoryCrawlerConfig(
                workDir, CrawlConfig.class, RANDOMIZER);
    }

    public static CrawlConfig randomMemoryCrawlerConfig(
            Path workDir,
            Class<? extends CrawlConfig> cfgClass,
            EasyRandom randomizer) {
        return randomizer.nextObject(cfgClass)
                .setId(CRAWLER_ID)
                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()));
    }

    public static Path writeConfigToDir(@NonNull CrawlConfig config) {
        var file = config
                .getWorkDir()
                .resolve(TimeIdGenerator.next() + ".yaml");
        try (Writer w = Files.newBufferedWriter(file)) {
            BeanMapper.DEFAULT.write(config, w, Format.YAML);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return file;
    }

    public static Path writeConfigToDir(
            Path workDir, Consumer<CrawlConfig> c) {
        var config = memoryCrawlerConfig(workDir);
        if (c != null) {
            c.accept(config);
        }
        return writeConfigToDir(config);
    }

    public static void writeOrUpdateConfigToFile(
            Path configFile, Consumer<CrawlConfig> c) {
        CrawlConfig config = null;
        if (Files.exists(configFile)) {
            config = new CrawlConfig();
            try (Reader r = Files.newBufferedReader(configFile)) {
                BeanMapper.DEFAULT.read(config, r,
                        Format.fromPath(configFile, Format.JSON));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            config = toMemoryCrawlerConfig(configFile.getParent(), config);
        } else {
            config = memoryCrawlerConfig(configFile.getParent());
        }

        if (c != null) {
            c.accept(config);
        }
        try (Writer w = Files.newBufferedWriter(configFile)) {
            BeanMapper.DEFAULT.write(config, w, Format.YAML);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SafeVarargs
    private static <T> Randomizer<T> randomInstanceOf(
            Class<? extends T>... subtypes) {
        var easyRandom = new EasyRandom();
        return () -> {
            if (subtypes.length == 0)
                return null;
            var index = ThreadLocalRandom.current().nextInt(subtypes.length);
            return easyRandom.nextObject(subtypes[index]);
        };
    }

}
