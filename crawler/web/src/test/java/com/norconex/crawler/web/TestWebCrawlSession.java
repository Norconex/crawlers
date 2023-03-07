/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.web;

import static org.apache.commons.lang3.ArrayUtils.EMPTY_STRING_ARRAY;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.assertj.core.util.Files;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.crawler.WebCrawlerConfig;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * <p>
 * Utility class to simplify creating end-to-end tests. Default settings:
 * </p>
 * <ul>
 *   <li>1 crawler configured as:
 *     <ul>
 *       <li>1 thread</li>
 *       <li>Ignorables all ignored (sitemap, robots, ...)</li>
 *       <li>No delay between each page downloads.</li>
 *       <li>1 MemoryCommitter.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
@Data
@Getter(value = AccessLevel.NONE)
@Accessors(fluent = true)
@NoArgsConstructor(staticName = "prepare")
public class TestWebCrawlSession {

    private Consumer<CrawlSessionConfig> crawlSessionSetup;
    private Consumer<WebCrawlerConfig> crawlerSetup;
    private final List<String> startUrls = new ArrayList<>();

    public TestWebCrawlSession startUrls(String... startUrls) {
        CollectionUtil.setAll(this.startUrls, startUrls);
        return this;
    }

    public CrawlSession crawlSession() {
        var workDir = Files.newTemporaryFolder();
        var crawlSession = WebStubber.crawlSession(
                workDir.toPath(), startUrls.toArray(EMPTY_STRING_ARRAY));
        WebTestUtil.ignoreAllIgnorables(crawlSession);
        var sessConfig = crawlSession.getCrawlSessionConfig();
        Optional.ofNullable(crawlSessionSetup)
                .ifPresent(css -> css.accept(sessConfig));
        Optional.ofNullable(crawlerSetup)
                .ifPresent(cs -> sessConfig.getCrawlerConfigs()
                        .forEach(c -> cs.accept((WebCrawlerConfig) c)));
        return crawlSession;
    }

    public MemoryCommitter crawl() {
        var crawlSession = crawlSession();
        try {
            crawlSession.start();
            return WebTestUtil.getFirstMemoryCommitter(crawlSession);
        } finally {
            try {
                FileUtil.delete(crawlSession.getWorkDir().toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
