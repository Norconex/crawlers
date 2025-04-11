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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

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
import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.doc.operations.DocumentConsumer;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.operations.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.pipelines.queue.ReferencesProvider;
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
                    .scanClasspathForConcreteTypes(true)
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
                    .excludeType(DocumentConsumer.class::equals)
                    .excludeType(ReferencesProvider.class::equals));

    private StubCrawlerConfig() {
    }

    public static CrawlerConfig memoryCrawlerConfig(Path workDir) {
        return toMemoryCrawlerConfig(workDir, new CrawlerConfig());
    }

    public static CrawlerConfig memoryCrawlerConfig(
            Path workDir, Class<? extends CrawlerConfig> cfgClass) {
        return toMemoryCrawlerConfig(workDir, ClassUtil.newInstance(cfgClass));
    }

    /**
     * Takes an existing config and make it a "memory" config.
     * @param workDir test working directory
     * @param cfg crawler config
     * @return same config instance, for chaining
     */
    public static CrawlerConfig toMemoryCrawlerConfig(
            Path workDir, CrawlerConfig cfg) {
        return cfg.setId(CRAWLER_ID)
                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()));
    }

    public static CrawlerConfig randomMemoryCrawlerConfig(Path workDir) {
        return randomMemoryCrawlerConfig(
                workDir, CrawlerConfig.class, RANDOMIZER);
    }

    public static CrawlerConfig randomMemoryCrawlerConfig(
            Path workDir,
            Class<? extends CrawlerConfig> cfgClass,
            EasyRandom randomizer) {
        return randomizer.nextObject(cfgClass)
                .setId(CRAWLER_ID)
                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()));
    }

    public static Path writeConfigToDir(@NonNull CrawlerConfig config) {
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
            Path workDir, Consumer<CrawlerConfig> c) {
        var config = memoryCrawlerConfig(workDir);
        if (c != null) {
            c.accept(config);
        }
        return writeConfigToDir(config);
    }

    public static void writeOrUpdateConfigToFile(
            Path configFile, Consumer<CrawlerConfig> c) {
        CrawlerConfig config = null;
        if (Files.exists(configFile)) {
            config = new CrawlerConfig();
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
}
