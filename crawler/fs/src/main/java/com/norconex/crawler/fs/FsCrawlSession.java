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
package com.norconex.crawler.fs;

import java.util.Optional;

import com.norconex.crawler.core.cli.CliLauncher;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerImpl;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.session.CrawlSessionBuilder;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.fs.crawler.FsCrawlerConfig;
import com.norconex.crawler.fs.doc.FsDocRecord;
import com.norconex.crawler.fs.fetch.FileFetcherProvider;
import com.norconex.crawler.fs.pipeline.committer.FsCommitterPipeline;
import com.norconex.crawler.fs.pipeline.importer.FsImporterPipeline;
import com.norconex.crawler.fs.pipeline.queue.FsQueueInitializer;
import com.norconex.crawler.fs.pipeline.queue.FsQueuePipeline;
import com.norconex.crawler.fs.util.Fs;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FsCrawlSession {

    /**
     * Invokes the File System Crawler from the command line.
     * You can invoke it without any arguments to get a list of command-line
     * options.
     * @param args command-line options
     */
    public static void main(String[] args) {
        try {
            System.exit(launch(args));
        } catch (Exception e) {
            e.printStackTrace(System.err); //NOSONAR
            System.exit(1);
        }
    }

    public static int launch(String... args) {
        return CliLauncher.launch(
                initCrawlSessionBuilder(
                        CrawlSession.builder(),
                        new CrawlSessionConfig(FsCrawlerConfig.class)),
                args);
    }

    public static CrawlSession createSession(CrawlSessionConfig sessionConfig) {
        return initCrawlSessionBuilder(
                CrawlSession.builder(),
                Optional.ofNullable(sessionConfig).orElseGet(() ->
                        new CrawlSessionConfig(FsCrawlerConfig.class)))
                .build();
    }

    // Return same builder, for chaining
    static CrawlSessionBuilder initCrawlSessionBuilder(
            CrawlSessionBuilder builder, CrawlSessionConfig sessionConfig) {
        builder.crawlerFactory(
                (sess, cfg) -> Crawler.builder()
                    .crawlSession(sess)
                    .crawlerConfig(cfg)
                    .crawlerImpl(crawlerImplBuilder().build())
                    .build()
            )
            .crawlSessionConfig(sessionConfig);
        return builder;
    }


    static CrawlerImpl.CrawlerImplBuilder crawlerImplBuilder() {
        return CrawlerImpl.builder()
            .fetcherProvider(new FileFetcherProvider())
            .beforeCrawlerExecution(FsCrawlSession::logCrawlerInformation)
            .queueInitializer(new FsQueueInitializer())
            .queuePipeline(new FsQueuePipeline())
            .importerPipeline(new FsImporterPipeline())
            .committerPipeline(new FsCommitterPipeline())
            .crawlDocRecordType(FsDocRecord.class)
            .docRecordFactory(ctx -> new FsDocRecord(
                    ctx.reference()
                    //TODO What about depth, cached doc, etc? It should be
                    // set here unless this is really used just for queue
                    // initialization or set by caller
                    //, 999
                    ))
            ;
    }

    private static void logCrawlerInformation(Crawler crawler, boolean resume) {
        var cfg = Fs.config(crawler);
        LOG.info("""
            Enabled features:

            Metadata:
              Checksummer:    %s
              Deduplication:  %s
            Document:
              Checksummer:    %s
              Deduplication:  %s
            """.formatted(
                    yn(cfg.getMetadataChecksummer() != null),
                    yn(cfg.isMetadataDeduplicate()
                            && cfg.getMetadataChecksummer() != null),
                    yn(cfg.getDocumentChecksummer() != null),
                    yn(cfg.isDocumentDeduplicate()
                            && cfg.getDocumentChecksummer() != null)
            ));
    }
    private static String yn(boolean value) {
        return value ? "Yes" : "No";
    }
}
