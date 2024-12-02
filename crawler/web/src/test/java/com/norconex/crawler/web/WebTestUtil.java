/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.jeasy.random.FieldPredicates.inClass;
import static org.jeasy.random.FieldPredicates.named;
import static org.jeasy.random.FieldPredicates.ofType;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.NullInputStream;
import org.apache.commons.lang3.ArrayUtils;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.jeasy.random.api.Randomizer;
import org.jeasy.random.randomizers.misc.BooleanRandomizer;
import org.jeasy.random.randomizers.number.IntegerRandomizer;
import org.jeasy.random.randomizers.number.LongRandomizer;
import org.jeasy.random.randomizers.number.NumberRandomizer;
import org.jeasy.random.randomizers.text.StringRandomizer;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.CircularRange;
import com.norconex.commons.lang.io.CachedInputStream;
import com.norconex.commons.lang.map.Properties;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.commands.crawl.task.operations.DocumentConsumer;
import com.norconex.crawler.core.commands.crawl.task.operations.spoil.SpoiledReferenceStrategizer;
import com.norconex.crawler.core.commands.crawl.task.operations.spoil.impl.GenericSpoiledReferenceStrategizer;
import com.norconex.crawler.core.commands.crawl.task.pipelines.queue.ReferencesProvider;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.web.doc.operations.delay.DelayResolver;
import com.norconex.crawler.web.doc.operations.delay.impl.BaseDelayResolverConfig.DelayResolverScope;
import com.norconex.crawler.web.doc.operations.delay.impl.GenericDelayResolver;
import com.norconex.crawler.web.doc.operations.image.impl.FeaturedImageResolver;
import com.norconex.crawler.web.doc.operations.link.LinkExtractor;
import com.norconex.crawler.web.doc.operations.link.impl.DomLinkExtractor;
import com.norconex.crawler.web.doc.operations.recrawl.RecrawlableResolver;
import com.norconex.crawler.web.fetch.HttpFetcher;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcher;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcherConfig;
import com.norconex.crawler.web.fetch.impl.GenericHttpFetcherConfig.CookieSpec;
import com.norconex.crawler.web.fetch.impl.HttpAuthConfig;
import com.norconex.crawler.web.fetch.impl.HttpAuthMethod;
import com.norconex.crawler.web.robot.RobotsTxtProvider;
import com.norconex.crawler.web.robot.impl.StandardRobotsTxtProvider;
import com.norconex.crawler.web.sitemap.SitemapResolver;
import com.norconex.crawler.web.stubs.CrawlerStubs;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.doc.Doc;

import lombok.NonNull;

public final class WebTestUtil {

    //    private static final BeanMapper beanMapper = BeanMapper.DEFAULT;
    //    private static final BeanMapper beanMapper = BeanMapper.builder()
    //            .unboundPropertyMapping("", null)
    //            .build();
    //            new WebBeanMapperBuilderFactory()
    //                .apply(WebCrawlerConfig.class)
    //                .build();

    //
    //
    //    public static BeanMapper beanMapper() {
    //        return beanMapper;
    //    }

    public static final String TEST_CRAWLER_ID = "test-crawler";
    public static final String TEST_CRAWL_SESSION_ID = "test-session";

    private static EasyRandom easyRandom = new EasyRandom(
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

                    .excludeType(Grid.class::equals)
                    .excludeType(GridConnector.class::equals)
                    .excludeType(SitemapResolver.class::equals)
                    .excludeType(DocumentConsumer.class::equals)
                    .excludeType(FeaturedImageResolver.class::equals)
                    .excludeType(RecrawlableResolver.class::equals)
                    .excludeType(ReferencesProvider.class::equals)
                    .excludeType(BiPredicate.class::equals)

                    .randomize(Charset.class, () -> StandardCharsets.UTF_8)
                    .randomize(CircularRange.class, () -> {
                        int a = new NumberRandomizer().getRandomValue();
                        int b = new NumberRandomizer().getRandomValue();
                        return CircularRange.between(
                                Math.min(a, b),
                                Math.max(a, b));
                    })
                    .randomize(
                            CachedInputStream.class,
                            CachedInputStream::nullInputStream)
                    .randomize(HttpFetcher.class, GenericHttpFetcher::new)
                    .randomize(
                            RobotsTxtProvider.class,
                            StandardRobotsTxtProvider::new)
                    .randomize(
                            Pattern.class, () -> Pattern.compile(
                                    new StringRandomizer(20).getRandomValue()))
                    .randomize(DelayResolver.class, () -> {
                        var resolv = new GenericDelayResolver();
                        resolv.getConfiguration()
                                .setScope(DelayResolverScope.CRAWLER);
                        return resolv;
                    })
                    .randomize(DomLinkExtractor.class, () -> {
                        var extractor = new DomLinkExtractor();
                        extractor.getConfiguration().addLinkSelector("text");
                        return extractor;
                    })
                    .randomize(LinkExtractor.class, () -> {
                        var extractor = new DomLinkExtractor();
                        extractor.getConfiguration().addLinkSelector("text");
                        return extractor;
                    })
                    .randomize(
                            f -> "cookieSpec".equals(
                                    f.getName()),
                            () -> CookieSpec.STRICT)
                    .randomize(
                            named(HttpAuthConfig.Fields.method)
                                    .and(ofType(HttpAuthMethod.class))
                                    .and(inClass(HttpAuthConfig.class)),
                            randomizerOneOf(HttpAuthMethod.values())));

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
        return values[new Random().nextInt(values.length - 1)];
    }

    /**
     * Gets the {@link MemoryCommitter} from first committer of the first
     * crawler from a crawl session (assuming the first committer is
     * a {@link MemoryCommitter}).  If that committer does not
     * exists or is not a memory committer, an exception is thrown.
     * @param crawler crawl session
     * @return Memory committer
     */
    public static MemoryCommitter firstCommitter(@NonNull Crawler crawler) {
        return (MemoryCommitter) crawler
                .getCrawlerConfig()
                .getCommitters()
                .get(0);
    }

    public static GenericHttpFetcher firstHttpFetcher(
            @NonNull Crawler crawler) {
        return (GenericHttpFetcher) crawler
                .getCrawlerConfig()
                .getFetchers()
                .get(0);
    }

    public static GenericHttpFetcherConfig firstHttpFetcherConfig(
            @NonNull CrawlerConfig crawlerConfig) {
        return ((GenericHttpFetcher) crawlerConfig
                .getFetchers().get(0)).getConfiguration();
    }

    public static GenericHttpFetcherConfig firstHttpFetcherConfig(
            @NonNull Crawler crawler) {
        return firstHttpFetcher(crawler).getConfiguration();
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

    public static void ignoreAllIgnorables(Crawler crawler) {
        ignoreAllIgnorables((WebCrawlerConfig) crawler.getCrawlerConfig());
    }

    public static void ignoreAllIgnorables(WebCrawlerConfig config) {
        config.setCanonicalLinkDetector(null)
                .setRobotsMetaProvider(null)
                .setRobotsTxtProvider(null)
                .setSitemapLocator(null)
                .setSitemapResolver(null);
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
        return toString(WebTestUtil.class.getResourceAsStream(resourcePath));
    }

    public static MemoryCommitter runWithConfig(
            @NonNull Path workDir, @NonNull Consumer<WebCrawlerConfig> c) {
        //    public static MemoryCommitter runWithConfig(
        //            @NonNull Path workDir, @NonNull Consumer<WebCrawlerConfig> c) {
        var crawler = CrawlerStubs.memoryCrawler(workDir, c);

        //        var crawlerBuilder = CrawlerStubs.memoryCrawlerBuilder(workDir);
        //        c.accept((WebCrawlerConfig) crawlerBuilder.configuration());
        //        var crawler = crawlerBuilder.build();
        crawler.crawl();
        return firstCommitter(crawler);
    }
}
