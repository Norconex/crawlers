/* Copyright 2021-2022 Norconex Inc.
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core.monitor.MdcUtil;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.stop.CrawlSessionStopper;
import com.norconex.crawler.core.stop.CrawlSessionStopperException;

/**
 * Listens for STOP requests using a stop file.  The stop file
 * file is created under the working directory as {@value #STOP_FILE_NAME}.
 *
 * @since 2.0.0
 */
public class FileBasedStopper implements CrawlSessionStopper {

    private static final Logger LOG =
            LoggerFactory.getLogger(FileBasedStopper.class);

    public static final String STOP_FILE_NAME = ".crawlsession-stop";

    private CrawlSession startedCollector;
    private boolean monitoring;

    @Override
    public void listenForStopRequest(CrawlSession startedSession)
            throws CrawlSessionStopperException {
        startedCollector = startedSession;
        final Path stopFile = stopFile(startedSession);

        // If there is already a stop file and the crawl session is not running,
        // delete it
        if (stopFile.toFile().exists() && !startedSession.isRunning()) {
            LOG.info("Old stop file found, deleting it.");
            try {
                FileUtils.forceDelete(stopFile.toFile());
            } catch (IOException e) {
                throw new CrawlSessionStopperException(
                        "Could not delete old stop file.", e);
            }
        }

        ExecutorService execService = Executors.newSingleThreadExecutor();
        try {
            execService.submit(() -> {
                MdcUtil.setCrawlSessionId(startedSession.getId());
                Thread.currentThread().setName("Collector stop file monitor");
                monitoring = true;
                while(monitoring) {
                    if (stopFile.toFile().exists()) {
                        stopMonitoring(startedSession);
                        LOG.info("STOP request received.");
                        startedSession.stop();
                    }
                    Sleeper.sleepMillis(100);
                }
                return null;
            });
        } finally {
            execService.shutdownNow();
        }
    }

    @Override
    public void destroy() throws CrawlSessionStopperException {
        if (startedCollector != null) {
            stopMonitoring(startedCollector);
        }
        startedCollector = null;
    }

    @Override
    public boolean fireStopRequest() throws CrawlSessionStopperException {
        final Path stopFile = stopFile(startedCollector);

        if (!startedCollector.isRunning()) {
            LOG.info("CANNOT STOP: The Collector is not running.");
            return false;
        }

        if (stopFile.toFile().exists()) {
            LOG.info("CANNOT STOP: Stop already requested. Stop file: {}",
                    stopFile.toAbsolutePath());
            return false;
        }

        try {
            Files.createFile(stopFile);
        } catch (IOException e) {
            throw new CrawlSessionStopperException("Could not create stop file: "
                    + stopFile.toAbsolutePath(), e);
        }
        return true;
    }

    private synchronized void stopMonitoring(CrawlSession crawlSession)
            throws CrawlSessionStopperException {
        monitoring = false;
        Path stopFile = stopFile(crawlSession);
        try {
            Files.deleteIfExists(stopFile);
        } catch (IOException e) {
            throw new CrawlSessionStopperException(
                    "Cannot delete stop file: " + stopFile.toAbsolutePath(), e);
        }
    }
    private static Path stopFile(CrawlSession crawlSession) {
        return crawlSession.getWorkDir().resolve(STOP_FILE_NAME);
    }
}
