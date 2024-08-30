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
package com.norconex.crawler.web.stubs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.doc.operations.delay.impl.GenericDelayResolver;

import lombok.NonNull;

public final class CrawlerConfigStubs {

    public static final String CRAWLER_ID = "test-crawler";

    private CrawlerConfigStubs() {
    }

    public static WebCrawlerConfig memoryCrawlerConfig(Path workDir) {
        var cfg = (WebCrawlerConfig) new WebCrawlerConfig()
                .setId(CRAWLER_ID)
                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()));
        ((GenericDelayResolver) cfg.getDelayResolver())
                .getConfiguration().setDefaultDelay(Duration.ZERO);
        return cfg;
    }

    /**
     * <p>Random crawler config stub:</p>
     * <ul>
     *   <li>Single-threaded</li>
     *   <li>1 Memory Committer</li>
     *   <li>Random values for everything else.</li>
     * </ul>
     * @return random crawler config
     */
    public static WebCrawlerConfig randomMemoryCrawlerConfig(Path workDir) {
        var cfg = (WebCrawlerConfig) WebTestUtil.randomize(
                WebCrawlerConfig.class)
                .setId(CRAWLER_ID)
                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()));
        ((GenericDelayResolver) cfg.getDelayResolver())
                .getConfiguration().setDefaultDelay(Duration.ZERO);
        return cfg;
    }

    public static Path writeConfigToDir(
            Path workDir,
            @NonNull Consumer<WebCrawlerConfig> c) {
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
