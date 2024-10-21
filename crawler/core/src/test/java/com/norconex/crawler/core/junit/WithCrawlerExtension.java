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
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.CrawlerTestUtil;
import com.norconex.crawler.core.tasks.CrawlerTaskContext;

@Disabled
class WithCrawlerExtension implements
        BeforeEachCallback,
        AfterEachCallback,
        ParameterResolver {

    private static final String KEY_BASE = "CrawlerExtension";
    private static final String TEMP_DIR_KEY = KEY_BASE + "-tempDir";
    private static final String CRAWLER_KEY = KEY_BASE + "-crawler";

    @Override
    public void beforeEach(ExtensionContext context) throws Exception {

        //        // Create a temporary directory before each test
        //        var tempDir = Files.createTempDirectory("crawler-core");
        //        context.getStore(GLOBAL).put(TEMP_DIR_KEY, tempDir);
        //
        //        var annot = getAnnotation(context, WithCrawlerTest.class);
        //        var crawlerConfig = annot.randomConfig()
        //                ? CrawlerConfigStubs.randomMemoryCrawlerConfig(tempDir)
        //                : CrawlerConfigStubs.memoryCrawlerConfig(tempDir);
        //
        //        // apply custom config from text
        //        StringUtil.ifNotBlank(
        //                annot.config(), cfgStr -> BeanMapper.DEFAULT.read(
        //                        crawlerConfig,
        //                        new StringReader(cfgStr),
        //                        BeanMapper.detectFormat(cfgStr)));
        //
        //        // apply config modifier from consumer
        //        if (annot.configModifier() != null) {
        //            @SuppressWarnings("unchecked")
        //            var c = (Consumer<CrawlerConfig>) ClassUtil
        //                    .newInstance(annot.configModifier());
        //            c.accept(crawlerConfig);
        //        }
        //
        //        //        var crawler = CrawlerStubs
        //        //                .memoryCrawlerBuilder(tempDir)
        //        //                .configuration(crawlerConfig)
        //        //                .build();
        //        var crawler = CrawlerStubs.crawler(tempDir, crawlerConfig);
        //
        //        context.getStore(GLOBAL).put(CRAWLER_KEY, crawler);
        //
        //        if (annot.run()) {
        //            crawler.start();
        //        } else {
        //            CrawlerTestUtil.initCrawler(crawler);
        //        }
    }

    @Override
    public void afterEach(ExtensionContext context) throws IOException {
        // End session
        var crawler = (CrawlerTaskContext) context.getStore(GLOBAL)
                .remove(CRAWLER_KEY);
        // if not already ended normally, stop it.
        if (crawler != null && !crawler.getState().isTerminatedProperly()) {
            CrawlerTestUtil.destroyCrawler(crawler);
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
        return CrawlerTaskContext.class.isAssignableFrom(parameterType)
                || MemoryCommitter.class.isAssignableFrom(parameterType)
                || Path.class.isAssignableFrom(parameterType);
    }

    @Override
    public Object resolveParameter(
            ParameterContext parameterContext,
            ExtensionContext extensionContext)
            throws ParameterResolutionException {

        Class<?> parameterType = parameterContext.getParameter().getType();

        // Crawler
        if (CrawlerTaskContext.class.isAssignableFrom(parameterType)) {
            return crawler(extensionContext).orElse(null);
        }
        // First committer
        if (MemoryCommitter.class.isAssignableFrom(parameterType)) {
            return firstCommitter(extensionContext).orElse(null);
        }
        // Temp dir.
        if (Path.class.isAssignableFrom(parameterType)) {
            return extensionContext.getStore(
                    ExtensionContext.Namespace.GLOBAL).remove(TEMP_DIR_KEY);
        }

        throw new IllegalArgumentException(
                "Unsupported parameter type: " + parameterType);
    }

    private Optional<CrawlerTaskContext>
            crawler(ExtensionContext extensionContext) {
        return ofNullable(
                (CrawlerTaskContext) extensionContext
                        .getStore(GLOBAL).get(CRAWLER_KEY));
    }

    private Optional<MemoryCommitter> firstCommitter(
            ExtensionContext extensionContext) {
        return crawler(extensionContext)
                .map(crwl -> crwl.getConfiguration().getCommitters())
                .filter(cmtrs -> !cmtrs.isEmpty())
                .map(cmtrs -> cmtrs.get(0))
                .map(MemoryCommitter.class::cast);
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
