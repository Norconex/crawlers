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
package com.norconex.crawler.core.junit;

import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL;

import java.io.IOException;
import java.io.StringReader;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.Consumer;

import org.apache.commons.lang3.ArrayUtils;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.crawler.core.CoreStubber2;
import com.norconex.crawler.core.TestUtil;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.core.session.MockCrawlSession;

class CrawlSessionExtension implements
        BeforeEachCallback,
        AfterEachCallback,
        ParameterResolver {

    private static final String KEY_BASE = "WithinCrawlSession";
    private static final String TEMP_DIR_KEY = KEY_BASE + "-tempDir";
    private static final String CRAWL_SESSION_KEY = KEY_BASE + "-crawlSession";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {

        // Create a temporary directory before each test
        var tempDir = Files.createTempDirectory("crawler-core");
        context.getStore(GLOBAL).put(TEMP_DIR_KEY, tempDir);

        var annot = resolveWithinCrawlSession(context);
        var sessionConfig = annot.randomDefaultConfig()
                ? CoreStubber2.crawlSessionConfigRandom(tempDir)
                : CoreStubber2.crawlSessionConfig(tempDir);

        // apply custom config
        StringUtil.ifNotBlank(annot.crawlSessionConfiguration(), cfg -> {
            TestUtil.beanMapper().read(
                    sessionConfig,
                    new StringReader(cfg),
                    TestUtil.detectConfigFormat(cfg));
        });

        var session = CoreStubber2.crawlSession(tempDir, sessionConfig);

        context.getStore(GLOBAL).put(CRAWL_SESSION_KEY, session);

        if (hasAnnotation(context, AfterCrawlSessionTest.class)) {
            session.getService().start();
        } else {
            session.sneakyInitCrawlSession();
        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        // End session
        var session = (MockCrawlSession) context
                .getStore(GLOBAL).remove(CRAWL_SESSION_KEY);
        // if not already ended normally, stop it.
        if (session != null) {
            session.sneakyDestroyCrawlSession();
        }

        // Clean up the temporary directory after each test
        var tempDir = (Path) context.getStore(
                ExtensionContext.Namespace.GLOBAL).remove(TEMP_DIR_KEY);
        if (tempDir != null) {
            Files.walk(tempDir)
                // Delete files before directories
                .sorted((path1, path2) -> path2.compareTo(path1))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        throw new RuntimeException(
                                "Failed to delete file: " + path, e);
                    }
                });
        }
    }

    @Override
    public boolean supportsParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext)
                    throws ParameterResolutionException {

        Class<?> parameterType = parameterContext.getParameter().getType();
        return CrawlSession.class.isAssignableFrom(parameterType)
                || Crawler.class.isAssignableFrom(parameterType)
                || MemoryCommitter.class.isAssignableFrom(parameterType)
                || Path.class.isAssignableFrom(parameterType);
    }
    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext)
                    throws ParameterResolutionException {

        Class<?> parameterType = parameterContext.getParameter().getType();

        if (CrawlSession.class.isAssignableFrom(parameterType)) {
            return crawlSession(extensionContext).orElse(null);
        }
        // First crawler
        if (Crawler.class.isAssignableFrom(parameterType)) {
            return firstCrawler(extensionContext).orElse(null);
        }
        // First committer
        if (MemoryCommitter.class.isAssignableFrom(parameterType)) {
            return firstCommitter(extensionContext).orElse(null);
        }
        // Temp dir.
        if (Path.class.isAssignableFrom(parameterType)) {
            return  extensionContext.getStore(
                    ExtensionContext.Namespace.GLOBAL).remove(TEMP_DIR_KEY);
        }

        throw new IllegalArgumentException(
                "Unsupported parameter type: " + parameterType);
    }


    private Optional<CrawlSession> crawlSession(
            ExtensionContext extensionContext) {
        return ofNullable((MockCrawlSession) extensionContext
                .getStore(GLOBAL).remove(CRAWL_SESSION_KEY));
    }
    private Optional<Crawler> firstCrawler(ExtensionContext extensionContext) {
        return crawlSession(extensionContext)
                .map(CrawlSession::getCrawlers)
                .filter(crwls -> !crwls.isEmpty())
                .map(crwls -> crwls.get(0));
    }
    private Optional<MemoryCommitter> firstCommitter(
            ExtensionContext extensionContext) {
        return firstCrawler(extensionContext)
                .map(crwl -> crwl.getConfiguration().getCommitters())
                .filter(cmtrs -> !cmtrs.isEmpty())
                .map(cmtrs -> cmtrs.get(0))
                .map(MemoryCommitter.class::cast);
    }

//    private boolean isStartingSession() {
//
//    }

    private WithinCrawlSessionTest resolveWithinCrawlSession(
            ExtensionContext context) {
        return ofNullable(getAnnotation(context, WithinCrawlSessionTest.class))
            .orElseGet(() -> new WithinCrawlSessionTest() {
                @Override
                public Class<? extends Annotation> annotationType() {
                    return WithinCrawlSessionTest.class;
                }
                @Override
                public boolean randomDefaultConfig() {
                    return false;
                }
                @Override
                public String[] crawlerConfigurations() {
                    return ArrayUtils.EMPTY_STRING_ARRAY;
                }
                @Override
                public Class<? extends Consumer<? extends CrawlerConfig>>
                        crawlerConfigModifier() {
                    return null;
                }
                @Override
                public String crawlSessionConfiguration() {
                    return "";
                }
                @Override
                public Class<? extends Consumer<? extends CrawlSessionConfig>>
                        crawlSessionConfigModifier() {
                    return null;
                }
            });
    }

    private boolean hasAnnotation(
            ExtensionContext ctx, Class<? extends Annotation> annotClass) {
        if (ctx.getRequiredTestMethod().isAnnotationPresent(annotClass)) {
            return true;
        }
        return ctx.getRequiredTestClass().isAnnotationPresent(annotClass);
    }

    private <T extends Annotation> T getAnnotation(
            ExtensionContext context, Class<T> annotClass) {
        // Check for annotation on the test method
        var testMethod = context.getRequiredTestMethod();
        if (testMethod.isAnnotationPresent(annotClass)) {
            return testMethod.getAnnotation(annotClass);
        }

        // Check for annotation on the test class
        Class<?> testClass = context.getRequiredTestClass();
        if (testClass.isAnnotationPresent(annotClass)) {
            return testClass.getAnnotation(annotClass);
        }
        return null;
    }
}

