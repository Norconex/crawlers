/* Copyright 2021-2024 Norconex Inc.
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
package com.norconex.crawler.core.stop.impl;

import com.norconex.crawler.core.stop.CrawlerStopper;
import com.norconex.crawler.core.stop.CrawlerStopperException;
import com.norconex.crawler.core.tasks.TaskContext;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * Listens for STOP requests using a stop file.  The stop file
 * file is created under the working directory as {@value #STOP_FILE_NAME}.
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class FileBasedStopper implements CrawlerStopper {

    //    public static final String STOP_FILE_NAME = ".crawlsession-stop";
    //    private Path monitoredStopFile;
    //    private boolean monitoring;

    @Override
    public void listenForStopRequest(TaskContext crawler)
            throws CrawlerStopperException {
        //        monitoredStopFile = stopFile(crawler);
        //
        //        // If there is already a stop file and the crawl session is not running,
        //        // delete it
        //        if (Files.exists(monitoredStopFile)
        //                && !crawler.getState().isExecutionLocked()) {
        //            LOG.info("Old stop file found, deleting it.");
        //            try {
        //                FileUtils.forceDelete(monitoredStopFile.toFile());
        //            } catch (IOException e) {
        //                throw new CrawlerStopperException(
        //                        "Could not delete old stop file.", e);
        //            }
        //        }
        //
        //        var scheduler = Executors.newScheduledThreadPool(1);
        //        monitoring = true;
        //        scheduler.scheduleAtFixedRate(() -> {
        //            LogUtil.setMdcCrawlerId(crawler.getId());
        //            Thread.currentThread().setName(
        //                    crawler.getId() + "-stop-file-monitor");
        //            if (monitoring && Files.exists(monitoredStopFile)) {
        //                stopMonitoring();
        //                LOG.info("STOP request received.");
        //                crawler.stop();
        //                scheduler.shutdownNow();
        //            } else if (!monitoring && !scheduler.isShutdown()) {
        //                scheduler.shutdownNow();
        //            }
        //        }, 1000, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws CrawlerStopperException {
        //        stopMonitoring();
    }

    private synchronized void stopMonitoring()
            throws CrawlerStopperException {
        //        monitoring = false;
        //        try {
        //            if (monitoredStopFile != null) {
        //                Files.deleteIfExists(monitoredStopFile);
        //            }
        //        } catch (IOException e) {
        //            throw new CrawlerStopperException(
        //                    "Cannot delete stop file: "
        //                            + monitoredStopFile.toAbsolutePath(),
        //                    e);
        //        }
    }

    @Override
    public boolean fireStopRequest(TaskContext crawler)
            throws CrawlerStopperException {
        //        final var stopFile = stopFile(crawler);
        //
        //        if (!crawler.getState().isExecutionLocked()) {
        //            LOG.info("Cannot stop local instance: No crawl session running.");
        //            return false;
        //        }
        //
        //        if (stopFile.toFile().exists()) {
        //            LOG.info(
        //                    "Cannot stop local instance: "
        //                            + "Stop already requested. Stop file: {}",
        //                    stopFile.toAbsolutePath());
        //            return false;
        //        }
        //
        //        try {
        //            Files.createFile(stopFile);
        //        } catch (IOException e) {
        //            throw new CrawlerStopperException(
        //                    "Could not create stop file: "
        //                            + stopFile.toAbsolutePath(),
        //                    e);
        //        }
        return true;
    }

    //    private static Path stopFile(Crawler crawler) {
    //        return crawler.getWorkDir().resolve(STOP_FILE_NAME);
    //    }
}
