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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.extension.Extension;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.junit.jupiter.api.extension.TestTemplateInvocationContext;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.util.ConfigUtil;

import lombok.Getter;

class CrawlerTestInvocationContext
        implements TestTemplateInvocationContext {

    private static final String STORE_KEY = "CrawlerTestInvocationContext";
    private static final String CRAWLER_KEY = "Crawler";

    @Getter
    private final Class<? extends GridConnector> gridConnectorClass;
    @Getter
    private final CrawlerTest annotation;

    CrawlerTestInvocationContext(
            Class<? extends GridConnector> gridConnectorClass,
            CrawlerTest annotation,
            ExtensionContext extensionContext) {
        this.gridConnectorClass = gridConnectorClass;
        this.annotation = annotation;
        JunitStore.set(extensionContext, STORE_KEY, this);
    }

    public static CrawlerTestInvocationContext get(ExtensionContext ctx) {
        return JunitStore.get(ctx, STORE_KEY);
    }

    public static Optional<Crawler> getCrawler(ExtensionContext ctx) {
        return Optional.ofNullable(JunitStore.get(ctx, CRAWLER_KEY));
    }

    public static void removeCrawler(ExtensionContext ctx) {
        JunitStore.remove(ctx, CRAWLER_KEY);
    }

    public static void setCrawler(ExtensionContext ctx, Crawler crawler) {
        JunitStore.set(ctx, CRAWLER_KEY, crawler);
    }

    @Override
    public List<Extension> getAdditionalExtensions() {

        return List.of(new ParameterResolver() {
            @Override
            public boolean supportsParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext)
                    throws ParameterResolutionException {

                Class<?> parameterType =
                        parameterContext.getParameter().getType();
                return Crawler.class.isAssignableFrom(parameterType)
                        || CrawlerContext.class
                                .isAssignableFrom(parameterType)
                        || MemoryCommitter.class
                                .isAssignableFrom(parameterType)
                        || Path.class.isAssignableFrom(parameterType);
            }

            @Override
            public Object resolveParameter(
                    ParameterContext parameterContext,
                    ExtensionContext extensionContext)
                    throws ParameterResolutionException {

                Class<?> paramType = parameterContext.getParameter().getType();

                var crawlerOpt = getCrawler(extensionContext);

                // Crawler
                if (Crawler.class.isAssignableFrom(paramType)) {
                    return crawlerOpt.get();
                }
                // CrawlerContext
                if (CrawlerContext.class.isAssignableFrom(paramType)) {
                    return CrawlerContext.get();
                    //return crawlerOpt.map(Crawler::getContext).orElse(null);
                }
                // First committer
                if (MemoryCommitter.class.isAssignableFrom(paramType)) {
                    return crawlerOpt
                            .map(crwl -> crwl
                                    .getCrawlerConfig()
                                    .getCommitters())
                            .filter(cmtrs -> !cmtrs.isEmpty())
                            .map(cmtrs -> cmtrs.get(0))
                            .map(MemoryCommitter.class::cast)
                            .orElse(null);
                }
                // Temp dir.
                if (Path.class.isAssignableFrom(paramType)) {
                    return crawlerOpt
                            .map(Crawler::getCrawlerConfig)
                            .map(ConfigUtil::resolveWorkDir)
                            .orElse(null);
                }

                throw new IllegalArgumentException(
                        "Unsupported parameter type: " + paramType);
            }
        });
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return String.format("WebCrawler test with : %s",
                gridConnectorClass.getClass().getSimpleName());
    }
}