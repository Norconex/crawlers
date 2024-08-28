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
package com.norconex.crawler.fs.stubs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlerConfig;

import lombok.NonNull;

public final class CrawlerConfigStubs {

    public static final String CRAWLER_ID = "test-crawler";

    private CrawlerConfigStubs() {
    }

    public static CrawlerConfig memoryCrawlerConfig(Path workDir) {
        return new CrawlerConfig()
                .setId(CRAWLER_ID)
                .setNumThreads(1)
                .setWorkDir(workDir)
                .setCommitters(List.of(new MemoryCommitter()));
    }

    public static Path writeConfigToDir(
            Path workDir,
            @NonNull Consumer<CrawlerConfig> c
    ) {
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
