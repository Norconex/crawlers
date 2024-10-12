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
package com.norconex.crawler.core;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang3.function.FailableRunnable;
import org.junit.platform.commons.JUnitException;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.crawler.core.stubs.CrawlerStubs;

import lombok.Data;
import lombok.NonNull;

public final class CrawlerTestUtil {

    @Data
    public static class Exit {
        private int code = -1;
        private String stdOut;
        private String stdErr;
        private final List<String> events = new ArrayList<>();

        public boolean ok() {
            return code == 0;
        }
    }

    private CrawlerTestUtil() {
    }

    public static void initCrawler(Crawler crawler) {
        //        GridCrawlerTaskExecutor.initLocalCrawler(crawler);
        //        CrawlerCommandExecuter.init(new CommandExecution(crawler, "TEST"));
    }

    public static void destroyCrawler(Crawler crawler) {
        //        GridCrawlerTaskExecutor.shutdownLocalCrawler(crawler);
        //        CrawlerCommandExecuter.orderlyShutdown(
        //                new CommandExecution(crawler, "TEST"));
    }

    public static MemoryCommitter firstCommitter(
            @NonNull Crawler crawler) {
        return (MemoryCommitter) crawler.getConfiguration().getCommitters()
                .get(0);
    }

    public static MemoryCommitter runWithConfig(
            @NonNull Path workDir, @NonNull Consumer<CrawlerConfig> c) {
        return null;
        //        var crawler = CrawlerStubs.memoryCrawler(workDir, c);
        //        //        var crawlerBuilder = CrawlerStubs.memoryCrawlerBuilder(workDir);
        //        //        c.accept(crawlerBuilder.configuration());
        //        //        var crawler = crawlerBuilder.build();
        //        crawler.start();
        //        return CrawlerTestUtil.firstCommitter(crawler);
    }

    public static void dumpStoreKeys(Crawler crawler) {
        //        initCrawler(crawler);
        //        var engine = crawler.getDataStoreEngine();
        //        System.err.println("KEY DUMP FOR ALL STORES:");
        //        engine.getStoreNames().forEach(sn -> {
        //            System.err.println();
        //            System.err.println("[" + sn + "]");
        //            DataStore<?> store =
        //                    engine.openStore(sn, engine.getStoreType(sn).get());
        //            store.forEach((k, v) -> {
        //                System.err.println("" + k);
        //                return true;
        //            });
        //        });
        //        destroyCrawler(crawler);
    }

    public static MemoryCommitter withinInitializedCrawler(
            @NonNull Path workDir,
            Consumer<CrawlerConfig> configModifier,
            @NonNull FailableRunnable<Exception> runnable) {
        var crawler = CrawlerStubs.memoryCrawler(workDir, configModifier);
        initCrawler(crawler);
        try {
            runnable.run();
        } catch (Exception e) {
            throw new CrawlerException("Exception during test.", e);
        }
        destroyCrawler(crawler);
        return CrawlerTestUtil.firstCommitter(crawler);
    }

    public static Exit cliLaunch(
            @NonNull Path workDir,
            String... cmdArgs) throws IOException {
        return cliLaunch(workDir, null, cmdArgs);
    }

    public static Exit cliLaunch(
            @NonNull Path workDir,
            Consumer<CrawlerConfig> configModifier,
            String... cmdArgs) throws IOException {

        throw new JUnitException("Not implemented.");

        //        //NOTE configuration will be read from file, but applied on top
        //        // of existing config so we can pre-configure items here.
        //        var exit = new Exit();
        //        var cfgFile = CrawlerConfigStubs.writeConfigToDir(workDir, cfg -> {
        //            if (configModifier != null) {
        //                configModifier.accept(cfg);
        //            }
        //        });
        //
        //        // replace config path with created path if argument was supplied
        //        // without a file
        //        for (var i = 0; i < cmdArgs.length; i++) {
        //            var arg = cmdArgs[i];
        //            if (StringUtils.equalsAny(arg, "-config=", "-c=")) {
        //                cmdArgs[i] = "-config=" + cfgFile;
        //            }
        //        }
        //
        //        new MutableObject<CrawlerConfig>();
        //
        //        Captured<Integer> captured = SystemUtil.callAndCaptureOutput(
        //                () -> CliCrawlerLauncher.launch(CrawlerStubs
        //                        .memoryCrawlerBuilder(workDir, cfg -> {
        //                            cfg.addEventListener(
        //                                    event -> exit
        //                                            .getEvents().add(event.getName()));
        //                        }),
        //                        cmdArgs));
        //
        //        exit.setCode(captured.getReturnValue());
        //        exit.setStdOut(captured.getStdOut());
        //        exit.setStdErr(captured.getStdErr());
        //        return exit;
    }
}
