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
package com.norconex.crawler.core;

import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.session.CrawlSessionConfig;

import lombok.Builder;
import lombok.Getter;
import lombok.NonNull;

@Builder(builderMethodName = "")
@Getter
public class SingleCrawlerTestContext {

    @NonNull
    private final Path workDir;
    @NonNull
    private final List<String> startReferences;

    private Consumer<CrawlSessionConfig> sessionConfigModifier;
    private Consumer<CrawlerConfig> crawlerConfigModifier;
    private Consumer<CrawlerImpl.CrawlerImplBuilder> crawlerImplBuilderModifier;

    public static SingleCrawlerTestContextBuilder builder(
            @NonNull Path workDir, String... startReferences) {
        return new SingleCrawlerTestContextBuilder()
                .workDir(workDir)
                .startReferences(List.of(startReferences));
    }
}
