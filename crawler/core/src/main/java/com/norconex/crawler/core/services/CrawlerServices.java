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
package com.norconex.crawler.core.services;

import static java.util.Optional.ofNullable;

import java.io.Closeable;

import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.service.CommitterService;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.event.EventManager;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.CrawlerCallbacks;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.services.monitor.CrawlerMonitor;
import com.norconex.importer.Importer;

import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;

@Builder
@Getter
public class CrawlerServices implements Closeable {

    private EventManager eventManager;

    private BeanMapper beanMapper;

    private CrawlerMonitor monitor;

    private DocTrackerService docTrackerService;

    //TODO have queue services? that way it will match pipelines?

    //TODO do we really need to make the committer service generic?
    private CommitterService<CrawlDoc> committerService;

    //TODO make general logging messages verbosity configurable
    private CrawlerProgressLogger progressLogger;

    //TODO do we really need to make the committer service generic?
    private Importer importer;

    // only applicable on start command
    private CrawlerCallbacks callbacks;

    @Default
    private DedupService dedupService = new DedupService();

    public void init(Crawler crawler) {

        docTrackerService.init();

        committerService.init(
                CommitterContext
                        .builder()
                        .setEventManager(getEventManager())
                        .setWorkDir(crawler.getWorkDir().resolve("committer"))
                        .setStreamFactory(crawler.getStreamFactory())
                        .build());

        dedupService.init(crawler);
    }

    @Override
    public void close() {
        // event managers is not closed here, but by the crawler
        // as the very last thing
        ofNullable(docTrackerService).ifPresent(DocTrackerService::close);
        ofNullable(committerService).ifPresent(CommitterService::close);
        ofNullable(dedupService).ifPresent(DedupService::close);
    }
}
