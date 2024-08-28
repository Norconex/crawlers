/* Copyright 2014-2024 Norconex Inc.
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

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.function.FailableRunnable;
import org.slf4j.MDC;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.time.DurationFormatter;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.services.monitor.CrawlerMonitorJMX;
import com.norconex.crawler.core.util.LogUtil;

import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

/**
 * Executes a crawler command, ensuring proper initialization and termination.
 */
@Slf4j
@EqualsAndHashCode
final class CrawlerCommandExecuter {

    private CrawlerCommandExecuter() {
    }

    static void executeCommand(CommandExecution execution) {
        try {
            init(execution);
            execution.command.run();
        } catch (Exception e) {
            throw new CrawlerException(
                    "An error occured while executing command: "
                            + execution.name,
                    e
            );
        } finally {
            orderlyShutdown(execution);
        }
    }

    static void init(CommandExecution execution) {
        var crawler = execution.crawler;

        //--- Ensure good state/config ---
        if (StringUtils.isBlank(crawler.getId())) {
            throw new CrawlerException(
                    "Crawler must be given a unique identifier (id)."
            );
        }
        LogUtil.setMdcCrawlerId(crawler.getId());
        Thread.currentThread().setName(
                crawler.getId() + "/" + execution.name
        );

        crawler.getServices().getEventManager().addListenersFromScan(
                crawler.getConfiguration()
        );

        crawler.fire(CrawlerEvent.CRAWLER_INIT_BEGIN);

        crawler.workDir = ofNullable(crawler.getConfiguration().getWorkDir())
                .orElseGet(() -> CrawlerConfig.DEFAULT_WORKDIR)
                .resolve(
                        FileUtil.toSafeFileName(
                                crawler.getId()
                        )
                );
        // Will also create workdir parent:
        crawler.tempDir = crawler.workDir.resolve("temp");
        try {
            // Will also create workdir parent:
            Files.createDirectories(crawler.tempDir);
        } catch (IOException e) {
            throw new CrawlerException(
                    "Could not create directory: "
                            + crawler.tempDir,
                    e
            );
        }
        crawler.streamFactory = new CachedStreamFactory(
                (int) crawler.getConfiguration().getMaxStreamCachePoolSize(),
                (int) crawler.getConfiguration().getMaxStreamCacheSize(),
                crawler.tempDir
        );

        if (execution.logIntro) {
            LogUtil.logCommandIntro(LOG, crawler);
        }
        crawler.getState().init(execution.lock);

        crawler.getDataStoreEngine().init(crawler);

        // clear if we want to have the crawler resetable (to rerun
        // the same instance)...
        //services.getEventManager().clearListeners();
        crawler.getServices().init(crawler);
        //        services.getEventManager().addListenersFromScan(configuration);

        if (Boolean.getBoolean(Crawler.SYS_PROP_ENABLE_JMX)) {
            CrawlerMonitorJMX.register(crawler);
        }

        crawler.getStopper().listenForStopRequest(crawler);

        crawler.fire(CrawlerEvent.CRAWLER_INIT_END);
        crawler.fire(eventBeginName(execution));
    }

    //TODO move this to a util "CrawlerShutdown" class?
    static void orderlyShutdown(CommandExecution execution) {
        var crawler = execution.crawler;

        crawler.fire(eventEndName(execution));
        crawler.fire(CrawlerEvent.CRAWLER_SHUTDOWN_BEGIN);
        try {
            // Defer shutdown
            ofNullable(crawler.getConfiguration().getDeferredShutdownDuration())
                    .filter(d -> d.toMillis() > 0)
                    .ifPresent(d -> {
                        LOG.info(
                                "Deferred shutdown requested. Pausing for {} "
                                        + "starting from this UTC moment: {}",
                                DurationFormatter.FULL.format(d),
                                LocalDateTime.now(ZoneOffset.UTC)
                        );
                        Sleeper.sleepMillis(d.toMillis());
                        LOG.info("Shutdown resumed.");
                    });

            // Unregister JMX crawlers
            if (Boolean.getBoolean(Crawler.SYS_PROP_ENABLE_JMX)) {
                LOG.info("Unregistering JMX crawler MBeans.");
                CrawlerMonitorJMX.unregister(crawler);
            }
            crawler.getDataStoreEngine().close();
            crawler.getServices().close();
        } finally {
            // close state last
            if (crawler.tempDir != null) {
                try {
                    FileUtil.delete(crawler.tempDir.toFile());
                } catch (IOException e) {
                    LOG.error(
                            "Could not delete the temporary directory:"
                                    + crawler.tempDir,
                            e
                    );
                }
            }
            MDC.clear();
            crawler.getState().setTerminatedProperly(true);
            crawler.getState().close();
            crawler.getStopper().destroy();
            crawler.fire(CrawlerEvent.CRAWLER_SHUTDOWN_END);
            crawler.getServices().getEventManager().clearListeners();
        }
    }

    private static String eventBeginName(CommandExecution execution) {
        return "CRAWLER_%s_BEGIN".formatted(execution.name);
    }

    private static String eventEndName(CommandExecution execution) {
        return "CRAWLER_%s_END".formatted(execution.name);
    }

    //--- Inner classes --------------------------------------------------------

    @Accessors(fluent = true)
    @Setter
    @RequiredArgsConstructor
    static class CommandExecution {
        private final Crawler crawler;
        private final String name;
        private boolean logIntro;
        // We only allow locking if the crawl state is initialized
        private boolean lock;
        private FailableRunnable<Exception> command;

        CommandExecution command(Runnable runnable) {
            command = runnable::run;
            return this;
        }

        CommandExecution failableCommand(FailableRunnable<Exception> runnable) {
            command = runnable;
            return this;
        }
    }

}
