/* Copyright 2021-2023 Norconex Inc.
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;

import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.stop.CrawlSessionStopper;
import com.norconex.crawler.core.stop.CrawlSessionStopperException;

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
public class FileBasedStopper implements CrawlSessionStopper {

    public static final String STOP_FILE_NAME = ".crawlsession-stop";
    private Path monitoredStopFile;
    private boolean monitoring;

    @Override
    public void listenForStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException {
        monitoredStopFile = stopFile(crawlSession);

        // If there is already a stop file and the crawl session is not running,
        // delete it
        if (Files.exists(monitoredStopFile)
                && !crawlSession.isInstanceRunning()) {
            LOG.info("Old stop file found, deleting it.");
            try {
                FileUtils.forceDelete(monitoredStopFile.toFile());
            } catch (IOException e) {
                throw new CrawlSessionStopperException(
                        "Could not delete old stop file.", e);
            }
        }

        var scheduler = Executors.newScheduledThreadPool(1);
        monitoring = true;
        scheduler.scheduleAtFixedRate(() -> {
            MdcUtil.setCrawlSessionId(crawlSession.getId());
            Thread.currentThread().setName(
                    crawlSession.getId() + "-stop-file-monitor");
            if (monitoring && Files.exists(monitoredStopFile)) {
                stopMonitoring();
                LOG.info("STOP request received.");
                crawlSession.getService().stop();
                scheduler.shutdownNow();
            } else if (!monitoring && !scheduler.isShutdown()) {
                scheduler.shutdownNow();
            }
        }, 1000, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void destroy() throws CrawlSessionStopperException {
        stopMonitoring();
    }

    private synchronized void stopMonitoring()
            throws CrawlSessionStopperException {
        monitoring = false;
        try {
            if (monitoredStopFile != null) {
                Files.deleteIfExists(monitoredStopFile);
            }
        } catch (IOException e) {
            throw new CrawlSessionStopperException(
                    "Cannot delete stop file: "
                            + monitoredStopFile.toAbsolutePath(), e);
        }
    }

    @Override
    public boolean fireStopRequest(CrawlSession crawlSession)
            throws CrawlSessionStopperException {
        final var stopFile = stopFile(crawlSession);

        if (!crawlSession.isInstanceRunning()) {
            LOG.info("Cannot stop local instance: No crawl session running.");
            return false;
        }

        if (stopFile.toFile().exists()) {
            LOG.info("Cannot stop local instance: "
                    + "Stop already requested. Stop file: {}",
                    stopFile.toAbsolutePath());
            return false;
        }

        try {
            Files.createFile(stopFile);
        } catch (IOException e) {
            throw new CrawlSessionStopperException(
                    "Could not create stop file: "
                            + stopFile.toAbsolutePath(), e);
        }
        return true;
    }

    private static Path stopFile(CrawlSession crawlSession) {
        return crawlSession.getWorkDir().resolve(STOP_FILE_NAME);
    }
}
