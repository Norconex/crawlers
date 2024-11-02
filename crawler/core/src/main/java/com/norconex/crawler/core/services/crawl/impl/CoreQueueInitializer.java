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
package com.norconex.crawler.core.services.crawl.impl;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.services.crawl.QueueInitContext;
import com.norconex.crawler.core.services.crawl.QueueInitializer;
import com.norconex.crawler.core.tasks.crawl.pipelines.queue.ReferencesProvider;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

/**
 * Default queue initializer, feeding the queue from a mix of references,
 * files containing references, and reference providers, each configurable
 * in {@link CrawlerConfig}.
 */
@Slf4j
@EqualsAndHashCode
public class CoreQueueInitializer implements QueueInitializer {

    public static final ToIntFunction<QueueInitContext> fromList = ctx -> {
        var cfg = ctx.getCrawlerContext().getConfiguration();
        var cnt = 0;
        for (String ref : cfg.getStartReferences()) {
            if (StringUtils.isNotBlank(ref)) {
                ctx.queue(ref);
                cnt++;
            }
        }
        if (cnt > 0) {
            LOG.info("Queued {} start references from list.", cnt);
        }
        return cnt;
    };

    public static final ToIntFunction<QueueInitContext> fromFiles = ctx -> {
        var cfg = ctx.getCrawlerContext().getConfiguration();
        var refsFiles = cfg.getStartReferencesFiles();
        var cnt = 0;
        for (Path refsFile : refsFiles) {
            try (var it = IOUtils.lineIterator(
                    Files.newInputStream(refsFile), StandardCharsets.UTF_8)) {
                while (it.hasNext()) {
                    var ref = StringUtils.trimToNull(it.nextLine());
                    if (ref != null && !ref.startsWith("#")) {
                        ctx.queue(ref);
                        cnt++;
                    }
                }
            } catch (IOException e) {
                throw new CrawlerException(
                        "Could not process references file: " + refsFile, e);
            }
        }
        if (cnt > 0) {
            LOG.info(
                    "Queued {} start references from {} files.",
                    cnt, refsFiles.size());
        }
        return cnt;
    };

    public static final ToIntFunction<QueueInitContext> fromProviders = ctx -> {
        var cfg = ctx.getCrawlerContext().getConfiguration();
        var providers = cfg.getStartReferencesProviders();
        var cnt = 0;
        for (ReferencesProvider provider : providers) {
            if (provider == null) {
                continue;
            }
            var it = provider.provideReferences();
            while (it.hasNext()) {
                ctx.queue(it.next());
                cnt++;
            }
        }
        if (cnt > 0) {
            LOG.info(
                    "Queued {} start references from {} providers.",
                    cnt, providers.size());
        }
        return cnt;
    };

    private final List<ToIntFunction<QueueInitContext>> initializers =
            new ArrayList<>();

    public CoreQueueInitializer() {
        initializers.add(fromList);
        initializers.add(fromFiles);
        initializers.add(fromProviders);
    }

    public CoreQueueInitializer(
            List<ToIntFunction<QueueInitContext>> initializers) {
        if (CollectionUtils.isNotEmpty(initializers)) {
            this.initializers.addAll(initializers);
        }
    }

    @Override
    public void accept(QueueInitContext ctx) {
        var cnt = 0;
        for (ToIntFunction<QueueInitContext> init : initializers) {
            cnt += init.applyAsInt(ctx);
        }
        if (LOG.isInfoEnabled()) {
            LOG.info("{} start URLs identified.",
                    NumberFormat.getNumberInstance().format(cnt));
        }
    }
}
