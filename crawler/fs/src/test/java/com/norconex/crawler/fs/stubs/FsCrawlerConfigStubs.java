/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.fs.stubs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlerConfig;

import lombok.NonNull;

public final class FsCrawlerConfigStubs {

    public static final String CRAWLER_ID = "test-crawler";

//    private static EasyRandom easyRandom = new EasyRandom(
//            new EasyRandomParameters()
//            .seed(System.currentTimeMillis())
//            .collectionSizeRange(1, 5)
//            .randomizationDepth(5)
//            .scanClasspathForConcreteTypes(true)
//            .overrideDefaultInitialization(true)
//            .randomize(File.class,
//                    () -> new File(new StringRandomizer(100).getRandomValue()))
//            .randomize(Path.class,
//                    () -> Path.of(new StringRandomizer(100).getRandomValue()))
//            .randomize(Long.class,
//                    () -> Math.abs(new LongRandomizer().getRandomValue()))
//            .randomize(DataStoreEngine.class, MVStoreDataStoreEngine::new)
////            .randomize(DataStore.class, MVStoreDataStore::new)
//            .randomize(ImporterConfig.class, ImporterConfig::new)
//            .randomize(UpsertRequest.class,
//                    () -> new UpsertRequest(
//                            new StringRandomizer(100).getRandomValue(),
//                            new Properties(),
//                            new NullInputStream()))
//            .randomize(DeleteRequest.class,
//                    () -> new DeleteRequest(
//                            new StringRandomizer(100).getRandomValue(),
//                            new Properties()))
//            .randomize(Committer.class, MemoryCommitter::new)
//            .randomize(Charset.class, () -> StandardCharsets.UTF_8)
//            .randomize(SpoiledReferenceStrategizer.class,
//                    GenericSpoiledReferenceStrategizer::new)
//            .randomize(AtomicBoolean.class, () -> new AtomicBoolean(
//                    new BooleanRandomizer().getRandomValue()))
//            .excludeType(DocumentConsumer.class::equals)
//            .excludeType(ReferencesProvider.class::equals)
//    );

    private FsCrawlerConfigStubs() {}

    public static CrawlerConfig memoryCrawlerConfig(Path workDir) {
        return new CrawlerConfig()
                .setId(CRAWLER_ID)
                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()))
                ;
    }
//    public static CrawlerConfig randomMemoryCrawlerConfig(Path workDir) {
//        return easyRandom.nextObject(CrawlerConfig.class)
//                .setId(CRAWLER_ID)
//                .setNumThreads(1)
//                .setWorkDir(workDir)
//                .setCommitters(List.of(new MemoryCommitter()));
//    }

    public static Path writeConfigToDir(
            Path workDir,
            @NonNull Consumer<CrawlerConfig> c) {
        var config = memoryCrawlerConfig(workDir);
        c.accept(config);
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
}
