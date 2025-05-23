/* Copyright 2023-2025 Norconex Inc.
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

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
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
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.doc.operations.checksum.DocumentChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.MetadataChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.impl.GenericMetadataChecksummer;
import com.norconex.crawler.core.doc.operations.checksum.impl.Md5DocumentChecksummer;
import com.norconex.crawler.core.doc.operations.filter.DocumentFilter;
import com.norconex.crawler.core.doc.operations.filter.MetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.OnMatch;
import com.norconex.crawler.core.doc.operations.filter.ReferenceFilter;
import com.norconex.crawler.core.doc.operations.filter.impl.ExtensionReferenceFilter;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericMetadataFilter;
import com.norconex.crawler.core.doc.operations.filter.impl.GenericReferenceFilter;
import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.operations.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.doc.pipelines.queue.ReferencesProvider;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.junit.CrawlTestCapturer;
import com.norconex.crawler.core.junit.CrawlTestCapturer.CrawlCaptures;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.crawler.fs.doc.operations.checksum.FsMetadataChecksummer;
import com.norconex.crawler.fs.mock.MockFsCrawlerBuilder;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.doc.Doc;

import lombok.NonNull;

public final class FsTestUtil {

    public static final String TEST_CRAWLER_ID = "test-crawler";
    public static final String TEST_CRAWL_SESSION_ID = "test-session";
    public static final String TEST_KEYSTORE_PATH =
            "src/test/resources/keystore.jks";
    public static final String TEST_FS_PATH =
            "src/test/resources/mock-fs";

    private static EasyRandom easyRandom = new EasyRandom(
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
                    .randomize(
                            Integer.class,
                            () -> Math.abs(
                                    new IntegerRandomizer().getRandomValue()))
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
                            randomInstanceOf(
                                    GenericMetadataChecksummer.class,
                                    FsMetadataChecksummer.class))
                    .randomize(
                            DocumentChecksummer.class,
                            Md5DocumentChecksummer::new)

                    .excludeType(Grid.class::equals)
                    .excludeType(GridConnector.class::equals)
                    .excludeType(StandardFileSystemManager.class::equals)
                    .excludeType(FileSystemOptions.class::equals)
                    .excludeType(ReferencesProvider.class::equals)
                    .excludeType(OnMatch.class::equals)
                    .excludeType(ReferenceFilter.class::equals));

    //MAYBE: maybe move some of the common test classes/methods to core
    // and make it a usable test artifact?

    private FsTestUtil() {
    }

    public static <T> T randomize(Class<T> cls) {
        return easyRandom.nextObject(cls);
    }

    public static Optional<UpsertRequest> getUpsertRequest(
            @NonNull MemoryCommitter mem, @NonNull String ref) {
        return mem.getUpsertRequests().stream()
                .filter(m -> ref.equals(m.getReference()))
                .findFirst();
    }

    public static String getUpsertRequestMeta(
            @NonNull MemoryCommitter mem,
            @NonNull String ref,
            @NonNull String fieldName) {
        return getUpsertRequest(mem, ref)
                .map(req -> req.getMetadata().getString(fieldName))
                .orElse(null);
    }

    public static String getUpsertRequestContent(
            @NonNull MemoryCommitter mem, @NonNull String ref) {
        return getUpsertRequest(mem, ref)
                .map(FsTestUtil::docText)
                .orElse(null);
    }

    public static Optional<DeleteRequest> getDeleteRequest(
            @NonNull MemoryCommitter mem, @NonNull String ref) {
        return mem.getDeleteRequests().stream()
                .filter(m -> ref.equals(m.getReference()))
                .findFirst();
    }

    public static Set<String> sortedRequestReferences(MemoryCommitter c) {
        return c.getAllRequests().stream()
                .map(CommitterRequest::getReference)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static Set<String> sortedUpsertReferences(MemoryCommitter c) {
        return c.getUpsertRequests().stream()
                .map(UpsertRequest::getReference)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static Set<String> sortedDeleteReferences(MemoryCommitter c) {
        return c.getDeleteRequests().stream()
                .map(DeleteRequest::getReference)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    public static String lastSortedRequestReference(MemoryCommitter c) {
        return sortedRequestReferences(c).stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public static String lastSortedUpsertReference(MemoryCommitter c) {
        return sortedUpsertReferences(c).stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public static String lastSortedDeleteReference(MemoryCommitter c) {
        return sortedDeleteReferences(c).stream()
                .reduce((first, second) -> second)
                .orElse(null);
    }

    public static ZonedDateTime daysAgo(int days) {
        return ZonedDateTime.now().minusDays(days).withNano(0);
    }

    public static String daysAgoRFC(int days) {
        return rfcFormat(daysAgo(days));
    }

    public static String rfcFormat(ZonedDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.RFC_1123_DATE_TIME);
    }

    public static String docText(Doc doc) {
        return toString(doc.getInputStream());
    }

    public static String docText(UpsertRequest doc) {
        return toString(doc.getContent());
    }

    public static String toString(InputStream is) {
        try {
            return IOUtils.toString(is, UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String resourceAsString(String resourcePath) {
        return toString(FsTestUtil.class.getResourceAsStream(resourcePath));
    }

    public static MemoryCommitter
            firstCommitter(@NonNull CrawlContext crawler) {
        return (MemoryCommitter) crawler.getCrawlConfig().getCommitters()
                .get(0);
    }

    public static MemoryCommitter firstCommitter(@NonNull Crawler crawler) {
        return (MemoryCommitter) crawler
                .getCrawlConfig()
                .getCommitters()
                .get(0);
    }

    public static CrawlCaptures crawlWithFetcher(
            Path tempDir, Fetcher fetcher, String... refs)
            throws Exception {
        return CrawlTestCapturer.capture(new MockFsCrawlerBuilder(tempDir)
                .configModifier(cfg -> cfg
                        .setStartReferences(List.of(refs))
                        .setFetchers(List.of(fetcher)))
                .crawler(), Crawler::crawl);
    }

    public static int freePort() {
        try (var serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
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
