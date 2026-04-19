/* Copyright 2024-2026 Norconex Inc.
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

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.admin.ClusterAdminClient;
import com.norconex.crawler.core.cluster.admin.ClusterAdminServer;
import com.norconex.crawler.core.util.ConfigUtil;

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
        var builder = ClusterAdminClient.builder()
                .crawlerId(crawlConfig.getId());
        if (nodeUrls.length > 0) {
            builder.nodeUrls(List.of(nodeUrls));
        } else {
            builder.nodeUrls(resolveNodeUrls());
        }
        var resp = builder.build().clusterStop();
        if (resp) {
            LOG.info("Stop request successfully sent.");
        }
    }

    /**
     * Resolves the list of admin URLs to probe, in priority order.
     * Tries the port file first (exact port the server advertised),
     * then falls back to scanning the full range the server may have
     * used (DEFAULT_ADMIN_PORT + up to 99 increments), so the stop
     * command works even when the file-based discovery has not yet
     * flushed to disk or is otherwise unavailable.
     */
    private List<String> resolveNodeUrls() {
        var basePort = crawlConfig.getClusterConfig().getAdminPort();
        var filePort = readPortFile();

        // Build an ordered list: port-file port first (if resolved), then
        // the full scan range so we always have a fallback.
        var urls = new ArrayList<String>();
        if (filePort != null) {
            LOG.info("Admin port file resolved to port {}.", filePort);
            urls.add("http://localhost:" + filePort);
        } else {
            LOG.warn("Admin port file not found or unreadable. "
                    + "Falling back to port scan from {}.", basePort);
        }
        // Add the scan range (skipping the port-file URL if already added).
        for (var p = basePort; p < basePort + 100; p++) {
            var url = "http://localhost:" + p;
            if (!urls.contains(url)) {
                urls.add(url);
            }
        }
        LOG.info("Will probe {} admin URL(s) starting with {}.",
                urls.size(), urls.get(0));
        return urls;
    }

    private Integer readPortFile() {
        if (crawlConfig.getWorkDir() == null) {
            return null;
        }
        var portFile = ConfigUtil.resolveWorkDir(crawlConfig)
                .resolve(ClusterAdminServer.ADMIN_PORT_FILE);
        LOG.info("Looking for admin port file at: {}", portFile);
        if (Files.exists(portFile)) {
            try {
                return Integer.parseInt(Files.readString(portFile).trim());
            } catch (IOException | NumberFormatException e) {
                LOG.warn("Could not read admin port file at {}.", portFile, e);
            }
        } else {
            LOG.warn("Admin port file does not exist: {}", portFile);
        }
        return null;
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
