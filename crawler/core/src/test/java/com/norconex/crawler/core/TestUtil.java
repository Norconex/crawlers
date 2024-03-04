/* Copyright 2017-2023 Norconex Inc.
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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.ClassUtils;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.SystemUtil.Captured;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.cli.CliLauncher;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;

import lombok.Data;
import lombok.NonNull;

public final class TestUtil {

    @Data
    public static class Exit {
        private int code = -1;
        private String stdOut;
        private String stdErr;
        private final List<String> events = new ArrayList<>();
        public boolean ok() {
            return code == 0;
        }
    }

    private static final BeanMapper BEAN_MAPPER = BeanMapper.builder()
            .unboundPropertyMapping("crawler", CrawlerMixIn.class)
            .build();

    private static class CrawlerMixIn {
        @JsonDeserialize(as = CrawlerConfig.class)
        private CrawlerConfig configuration;
    }

    public static BeanMapper beanMapper() {
        return BEAN_MAPPER;
    }


    private TestUtil() {}

    /**
     * Gets the {@link MemoryCommitter} from first committer of the first
     * crawler from a crawl session (assuming the first committer is
     * a {@link MemoryCommitter}).  If that committer does not
     * exists or is not a memory committer, an exception is thrown.
     * @param crawlSession crawl session
     * @return Memory committer
     */
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull CrawlSession crawlSession) {
        return (MemoryCommitter) getFirstCrawlerConfig(
                crawlSession).getCommitters().get(0);
    }
    public static MemoryCommitter getFirstMemoryCommitter(
            @NonNull Crawler crawler) {
        return (MemoryCommitter)
                crawler.getConfiguration().getCommitters().get(0);
    }
    public static Crawler getFirstCrawler(
            @NonNull CrawlSession crawlSession) {
        if (!crawlSession.getCrawlers().isEmpty()) {
            return crawlSession.getCrawlers().get(0);

        }
        return null;
    }
    public static CrawlerConfig getFirstCrawlerConfig(
            @NonNull CrawlSession crawlSession) {
        return crawlSession.getCrawlSessionConfig().getCrawlerConfigs().get(0);
    }

    // One crawler, one committer
    public static MemoryCommitter runSingleCrawler(
            @NonNull Path workDir,
            Consumer<CrawlerConfig> c,
            String... startReferences) {

        var sess = CoreStubber.crawlSession(workDir, startReferences);
        var cfg = TestUtil.getFirstCrawlerConfig(sess);
        if (c != null) {
            c.accept(cfg);
        }
        sess.start();
        waitForIt(sess);
        return TestUtil.getFirstMemoryCommitter(sess);
    }
    // One crawler, one committer
    public static MemoryCommitter runSingleCrawler(
            @NonNull Path workDir,
            Consumer<CrawlerConfig> c,
            Consumer<CrawlerImpl.CrawlerImplBuilder> crawlerImplBuilderModifier,
            String... startReferences) {

        var sess = CoreStubber.crawlSession(
                workDir, crawlerImplBuilderModifier, startReferences);
        var cfg = TestUtil.getFirstCrawlerConfig(sess);
        if (c != null) {
            c.accept(cfg);
        }
        sess.start();
        waitForIt(sess);
        return TestUtil.getFirstMemoryCommitter(sess);
    }

    private static void waitForIt(CrawlSession sess) {
        var cnt = 0;
        while (sess.isRunning()) {
            Sleeper.sleepMillis(100);
            cnt++;
            if (cnt == 100) {
                throw new IllegalStateException(
                        "Crawler did not stop in time.");
            }
        }
    }

    public static Exit testLaunch(
            @NonNull Path workDir, String... cmdArgs) throws IOException {
        return testLaunch(workDir, (List<String>) null, cmdArgs);
    }

    public static Exit testLaunch(
            @NonNull Path workDir,
            List<String> startReferences,
            String... cmdArgs) throws IOException {
        var exit = new Exit();
        var crawlSessionConfig = new CrawlSessionConfig();
        crawlSessionConfig.setWorkDir(workDir);
        crawlSessionConfig.addEventListener(
                event -> exit.getEvents().add(event.getName()));
        var refs = Optional.ofNullable(startReferences)
                .map(list -> list.toArray(ArrayUtils.EMPTY_STRING_ARRAY))
                .orElse(ArrayUtils.EMPTY_STRING_ARRAY);

        Captured<Integer> captured = SystemUtil.callAndCaptureOutput(() ->
                CliLauncher.launch(
                    CrawlSession.builder()
                        .crawlerConfigClass(CrawlerConfig.class)
                        .crawlerFactory((sess, cfg) -> Crawler.builder()
                            .crawlSession(sess)
                            .crawlerConfig(cfg)
                            .crawlerImpl(CoreStubber.crawlerImpl(cfg, refs))
                            .build())
                        .crawlSessionConfig(crawlSessionConfig),
                    cmdArgs));
        exit.setCode(captured.getReturnValue());
        exit.setStdOut(captured.getStdOut());
        exit.setStdErr(captured.getStdErr());
        return exit;
    }

    public static void testValidation(String xmlResource) throws IOException {
        testValidation(TestUtil.class.getResourceAsStream(xmlResource));

    }
    public static void validate(Class<?> clazz) throws IOException {
        testValidation(clazz, ClassUtils.getShortClassName(clazz) + ".xml");
    }
    public static void testValidation(Class<?> clazz, String xmlResource)
            throws IOException {
        testValidation(clazz.getResourceAsStream(xmlResource));

    }
    public static void testValidation(
            InputStream xmlStream) throws IOException {

        try (Reader r = new InputStreamReader(xmlStream)) {
            XML.of(r).create().validate();
        }
    }

    public static void withinInitializedSession(
            @NonNull Path workDir, @NonNull Consumer<CrawlSession> c) {
        var session = CoreStubber.crawlSession(workDir);
        session.sneakyInitCrawlSession();
        c.accept(session);
        session.sneakyDestroyCrawlSession();
    }
    public static void withinInitializedCrawler(
            @NonNull Path workDir, @NonNull Consumer<Crawler> c) {
        withinInitializedCrawler(workDir, c, null);
    }
    public static void withinInitializedCrawler(
            @NonNull Path workDir,
            @NonNull Consumer<Crawler> c,
            Consumer<CrawlerConfig> configModifier) {
        var crawler = CoreStubber.crawler(workDir);
        if (configModifier != null) {
            configModifier.accept(crawler.getConfiguration());
        }
        crawler.getCrawlSession().sneakyInitCrawlSession();
        crawler.sneakyInitCrawler();
        c.accept(crawler);
        crawler.sneakyDestroyCrawler();
        crawler.getCrawlSession().sneakyDestroyCrawlSession();
    }
}
