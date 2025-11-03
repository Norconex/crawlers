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
package com.norconex.crawler.core._DELETE;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.cluster.CacheManager;
import com.norconex.crawler.core.cluster.Cluster;
import com.norconex.crawler.core.cluster.pipeline.PipelineManager;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Resolves test method parameters. Invoked after
 * CrawlTestExtensionInitialization.
 */
public class CrawlTestParameterResolver implements ParameterResolver {

    @Override
    public boolean supportsParameter(ParameterContext parameterContext,
            ExtensionContext extensionContext) {
        Class<?> parameterType =
                parameterContext.getParameter().getType();
        return List.of(
                Crawler.class,
                CrawlConfig.class,
                CrawlSession.class,
                CrawlContext.class,
                Cluster.class,
                CacheManager.class,
                MemoryCommitter.class,
                Path.class)
                .stream()
                .anyMatch(cls -> cls.isAssignableFrom(parameterType));
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext,
            ExtensionContext extensionContext) {

        var params = CrawlTestParameters.get(extensionContext);

        Class<?> paramType = parameterContext.getParameter().getType();

        // Crawler
        if (Crawler.class.isAssignableFrom(paramType)) {
            return params.getCrawler();
        }

        // CrawlConfig
        if (CrawlConfig.class.isAssignableFrom(paramType)) {
            return params.getCrawlConfig();
        }

        // CrawlSession
        if (CrawlSession.class.isAssignableFrom(paramType)) {
            return params.getCrawlSession();
        }

        // CrawlContext
        if (CrawlContext.class.isAssignableFrom(paramType)) {
            return params.getCrawlSession().getCrawlContext();
        }

        // Cluster
        if (Cluster.class.isAssignableFrom(paramType)) {
            return params.getCrawlSession().getCluster();
        }

        // PipelineManager
        if (PipelineManager.class.isAssignableFrom(paramType)) {
            return params.getCrawlSession().getCluster().getPipelineManager();
        }

        // CacheManager
        if (CacheManager.class.isAssignableFrom(paramType)) {
            return params.getCrawlSession().getCluster().getCacheManager();
        }

        // First committer
        if (MemoryCommitter.class.isAssignableFrom(paramType)) {
            return params.getMemoryCommitter();
        }

        // Temp dir.
        if (Path.class.isAssignableFrom(paramType)) {
            return params.getWorkDir();
        }

        throw new IllegalArgumentException(
                "Unsupported test method parameter type: " + paramType);
    }
}
