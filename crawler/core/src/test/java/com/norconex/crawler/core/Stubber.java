/* Copyright 2022 Norconex Inc.
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

import static com.norconex.commons.lang.ResourceLoader.getXmlString;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.commons.lang3.mutable.MutableBoolean;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerConfig;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionConfig;

public final class Stubber {

    private Stubber() {}

    public static CrawlerConfig crawlerConfig() {
        var crawlerConfig = new CrawlerConfig();
        crawlerConfig.setId("test-crawler");
        crawlerConfig.setCommitters(List.of(new MemoryCommitter()));
        return crawlerConfig;
    }

    public static CrawlSessionConfig crawlSessionConfig() {
        var sessionConfig = new CrawlSessionConfig();
        sessionConfig.setId("test-session");
        sessionConfig.setCrawlerConfigs(List.of(crawlerConfig()));
        return sessionConfig;
    }

    public static Path writeSampleConfigToDir(Path dir) {

        // if config file does not exist, assume we want to use the
        // stubber default.
        var cfgFile = dir.resolve("Stubber.xml");
        if (!Files.exists(cfgFile)) {
            try {
                Files.writeString(cfgFile, getXmlString(Stubber.class));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return cfgFile;
    }

//    public static CrawlSession crawlSession(Path configFile) {
//        return crawlSession(configFile, null);
//    }
//    public static CrawlSession crawlSession(Path configFile, Path varsFile) {
//
//        // if config file does not exist, assume we want to use the
//        // stubber default.
//        if (!Files.exists(configFile)) {
//            try {
//                Files.writeString(configFile, getXmlString(Stubber.class));
//            } catch (IOException e) {
//                throw new UncheckedIOException(e);
//            }
//        }
//        return CrawlSession.builder()
//                .crawlerFactory((sess, cfg) -> Crawler.builder()
//                        .crawlSession(sess)
//                        .crawlerConfig(cfg)
//                        .build())
//                .crawlSessionConfig(new ConfigurationLoader()
//                        .setVariablesFile(varsFile)
//                        .loadFromXML(configFile, CrawlSessionConfig.class))
//                .build();
//    }

    public static CrawlSession crawlSession() {
        return CrawlSession.builder()
                .crawlerFactory((crawlSess, crawlerCfg) -> Crawler.builder()
                        .crawlSession(crawlSess)
                        .crawlerConfig(crawlerCfg)
                        .crawlerImpl(mockCrawlerImpl(crawlerCfg))
                        .build())
                .crawlSessionConfig(new CrawlSessionConfig())
                .build();
    }

    public static CrawlerImpl mockCrawlerImpl(CrawlerConfig crawlerConfig) {
        return CrawlerImpl.builder()
                .fetcherProvider(crawler -> new MockFetcher())
                .committerPipelineExecutor((crawler, doc) -> {})
                .crawlerConfig(crawlerConfig)
                .importerPipelineExecutor(ctx -> null)
                .queueInitializer((crawler, bool) -> new MutableBoolean())
                .queuePipelineExecutor(ctx -> {})
//                .childDocRecordFactory((ref, cached) ->
//                        new CrawlDocRecord(ref))
                .build();
    }
}
