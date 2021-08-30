/* Copyright 2017-2020 Norconex Inc.
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
package com.norconex.collector.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.Collection;
import java.util.function.Supplier;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.mutable.MutableObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.provider.Arguments;

import com.norconex.collector.core.CollectorEvent;
import com.norconex.collector.core.crawler.CrawlerEvent;
import com.norconex.collector.http.crawler.HttpCrawler;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.committer.core3.impl.MemoryCommitter;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanUtil;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.commons.lang.xml.XML;


/**
 * @author Pascal Essiembre
 * @since 1.8.0
 */
public final class TestUtil {

    private TestUtil() {
        super();
    }

    // create test a arguments instance with the object as the first agrument
    // and the simple class name of the object as the second.  For nicer
    // display in test reports.
    public static Arguments args(Object obj) {
        return Arguments.of(obj, obj.getClass().getSimpleName());
    }
    public static Arguments args(Supplier<Object> supplier) {
        return args(supplier.get());
    }

    // get config of the same name as class, but with .xml extension.
    public static HttpCollectorConfig loadCollectorConfig(
            Class<?> clazz) throws IOException {
        return loadCollectorConfig(clazz, clazz.getSimpleName() + ".xml");
    }
    // get config from resource relative to class
    public static HttpCollectorConfig loadCollectorConfig(
            Class<?> clazz, String xmlResource) throws IOException {
        HttpCollectorConfig cfg = new HttpCollectorConfig();
        try (Reader r = new InputStreamReader(
                clazz.getResourceAsStream(xmlResource))) {
            new XML(r).populate(cfg);
        }
        return cfg;
    }
    public static void testValidation(String xmlResource) throws IOException {
        testValidation(TestUtil.class.getResourceAsStream(xmlResource));

    }
    public static void testValidation(Class<?> clazz) throws IOException {
        testValidation(clazz, ClassUtils.getShortClassName(clazz) + ".xml");
    }
    public static void testValidation(Class<?> clazz, String xmlResource)
            throws IOException {
        testValidation(clazz.getResourceAsStream(xmlResource));

    }
    public static void testValidation(
            InputStream xmlStream) throws IOException {

        try (Reader r = new InputStreamReader(xmlStream)) {
            Assertions.assertTrue(
                    new XML(r).validate().isEmpty(),
                "Validation warnings/errors were found.");
        }
    }

    public static void assertSameEntries(
            Collection<?> expected, Collection<?> actual) {
        Assertions.assertEquals(expected.size(), actual.size(),
                "Wrong number of entries.");
        Assertions.assertTrue(actual.containsAll(expected),
                ("Does not contain the same entries. Diff:\n" + BeanUtil.diff(
                        new MutableObject<>(expected),
                        new MutableObject<>(actual))
                                .replaceAll("\\r\\n", "\n")
                                .replaceAll("\\n", " | ")
                                .replaceAll(" \\|  \\| ", "\n")
                                .replaceAll("\\| >", "\n>")));
    }


    //TODO move to HttpCollectorTestFactory or TestBuilder that returns a
    // test facade object with convinient test/getter methods.

    /**
     * Gets a single-crawler configuration that uses a {@link MemoryCommitter}
     * to store documents. Both the collector and its unique crawler are
     * guaranteed to have unique ids (UUID).
     * @param id collector id
     * @param workdir working directory
     * @param startURLs start URLs
     * @return HTTP Collector configuration
     * @throws IOException IO exception
     */
    public static HttpCollectorConfig newMemoryCollectorConfig(
            String id, Path workdir, String... startURLs) throws IOException {
        //--- Collector ---
        HttpCollectorConfig colConfig = new HttpCollectorConfig();
        colConfig.setId("Unit Test HTTP Collector " + id);

        colConfig.setWorkDir(workdir);

        MemoryCommitter committer = new MemoryCommitter();

        //--- Crawler ---
        HttpCrawlerConfig httpConfig = new HttpCrawlerConfig();
        httpConfig.setId("Unit Test HTTP Crawler " + id);
        httpConfig.setStartURLs(startURLs);
        httpConfig.setNumThreads(1);
        GenericDelayResolver resolver = new GenericDelayResolver();
        resolver.setDefaultDelay(0);
        httpConfig.setDelayResolver(resolver);
        httpConfig.setIgnoreCanonicalLinks(true);
        httpConfig.setIgnoreRobotsMeta(true);
        httpConfig.setIgnoreRobotsTxt(true);
        httpConfig.setIgnoreSitemap(true);
        httpConfig.setCommitters(committer);

        httpConfig.setMetadataChecksummer(null);
        httpConfig.setDocumentChecksummer(null);

        colConfig.setCrawlerConfigs(httpConfig);
        return colConfig;
    }


//    /**
//     * Gets a single-crawler that uses a {@link MemoryCommitter} to store
//     * documents.
//     * @param id collector id
//     * @param workdir working directory
//     * @param startURLs start URLs
//     * @return HTTP Collector
//     * @throws IOException
//     */
//    public static HttpCollector newMemoryCollector() throws IOException {
//        return new HttpCollector(
//                newMemoryCollectorConfig(UUID.randomUUID().toString(),
//                        FileUtils.getTempDirectory().toPath(), "N/A"));
//    }

    /**
     * Gets a single-crawler that uses a {@link MemoryCommitter} to store
     * documents.
     * @param id collector id
     * @param workdir working directory
     * @param startURLs start URLs
     * @return HTTP Collector
     * @throws IOException IO exception
     */
    public static HttpCollector newMemoryCollector(
            String id, Path workdir, String... startURLs) throws IOException {
        return new HttpCollector(
                newMemoryCollectorConfig(id, workdir, startURLs));
    }

    public static void mockCrawlerRunLifeCycle(
            Object eventReceiver, Runnable midCycleRunnable)
                    throws Exception {
        HttpCollector collector = newMemoryCollector(
                Long.toString(TimeIdGenerator.next()),
                FileUtils.getTempDirectory().toPath(), "N/A");
        HttpCrawler crawler = new HttpCrawler(
                (HttpCrawlerConfig) collector.getCollectorConfig()
                        .getCrawlerConfigs().get(0), collector);
        EventManager em = new EventManager();
        em.addListenersFromScan(eventReceiver);
        em.fire(new CollectorEvent.Builder(
                CollectorEvent.COLLECTOR_RUN_BEGIN, collector).build());
        em.fire(new CrawlerEvent.Builder(
                CrawlerEvent.CRAWLER_INIT_BEGIN, crawler).build());
        em.fire(new CrawlerEvent.Builder(
                CrawlerEvent.CRAWLER_INIT_END, crawler).build());
        em.fire(new CrawlerEvent.Builder(
                CrawlerEvent.CRAWLER_RUN_BEGIN, crawler).build());
        em.fire(new CrawlerEvent.Builder(
                CrawlerEvent.CRAWLER_RUN_THREAD_BEGIN, crawler)
                .subject(Thread.currentThread())
                .build());
        try {
            midCycleRunnable.run();
        } finally {
            em.fire(new CrawlerEvent.Builder(
                    CrawlerEvent.CRAWLER_RUN_THREAD_END, crawler)
                    .subject(Thread.currentThread())
                    .build());
            em.fire(new CrawlerEvent.Builder(
                    CrawlerEvent.CRAWLER_RUN_END, crawler).build());
            em.fire(new CollectorEvent.Builder(
                    CollectorEvent.COLLECTOR_RUN_END, collector).build());
            em.clearListeners();
        }
    }

    @FunctionalInterface
    public interface Runnable {
        void run() throws Exception;
    }
}
