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
package com.norconex.crawler.core.cmd.stop;

import java.util.List;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.admin.ClusterAdminClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Command to stop a running crawler session.
 */
@Slf4j
@RequiredArgsConstructor
public class StopCommand implements Runnable {

    private final CrawlConfig crawlConfig;
    private final String[] nodeUrls;

    @Override
    public void run() {
        var resp = ClusterAdminClient.builder()
                .nodeUrls(List.of(nodeUrls))
                .crawlerId(crawlConfig.getId())
                .build()
                .clusterStop();
        if (resp) {
            LOG.info("Stop request successfully sent.");
        }
    }

    //    @Override
    //    public void execute(CrawlSession session) {
    //        LOG.info("StopCommand.execute() ENTRY - sending stop signal");
    //        var ctx = session.getCrawlContext();
    //        Thread.currentThread().setName(ctx.getId() + "/STOP");
    //        session.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_BEGIN, this);
    //
    //        LOG.info("StopCommand: calling session.getCluster().stop()...");
    //        session.getCluster().stop();
    //
    //        LOG.info("Stop command executed - signal sent to cluster.");
    //
    //        session.fire(CrawlerEvent.CRAWLER_STOP_REQUEST_END, this);
    //    }
}
