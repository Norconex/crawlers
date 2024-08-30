/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.web.cases.recovery;

import static java.lang.String.join;

import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Environment;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.SystemUtil.Captured;
import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.bean.BeanMapper.Format;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.web.WebCrawler;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

/**
 * Launches a crawler from the command-line, in its own JVM.
 */
@Slf4j
public class ExternalCrawlSessionLauncher {

    private static final String committerDir = "external-test";

    public static CrawlOutcome start(CrawlerConfig crawlerConfig) {
        return launch(crawlerConfig, "start");
    }

    public static CrawlOutcome startClean(CrawlerConfig crawlerConfig) {
        return launch(crawlerConfig, "start -clean");
    }

    public static CrawlOutcome clean(CrawlerConfig crawlerConfig) {
        return launch(crawlerConfig, "clean");
    }

    public static CrawlOutcome launch(
            CrawlerConfig crawlerConfig,
            String action,
            String... extraArgs) {
        // make sure things are not happening to fast for our tests
        //Sleeper.sleepMillis(3000);
        var now = ZonedDateTime.now();
        Captured<Integer> captured = SystemUtil.callAndCaptureOutput(
                () -> doLaunch(crawlerConfig, action, extraArgs));
        var outcome = new CrawlOutcome(captured);
        var c = new TestCommitter(
                crawlerConfig.getWorkDir().resolve(committerDir));
        c.fillMemoryCommitters(outcome, now);
        try {
            c.close();
        } catch (CommitterException e) {
            throw new RuntimeException(e);
        }
        return outcome;
    }

    private static int doLaunch(
            CrawlerConfig crawlerConfig,
            String action,
            String... extraArgs) {

        if ("start".equalsIgnoreCase(action)) {
            addTestCommitterOnce(crawlerConfig);
        }

        // Save session config to file so it can be used from command-line
        var configFile = crawlerConfig.getWorkDir().resolve("config.xml");
        try (var writer = new FileWriter(configFile.toFile())) {
            BeanMapper.DEFAULT.write(crawlerConfig, writer, Format.XML);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save XML.", e);
        }

        var project = new Project();
        project.setBaseDir(crawlerConfig.getWorkDir().toFile());
        project.init();
        var logger = new DefaultLogger();
        project.addBuildListener(logger);
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        // Change to MSG_DEBUG to get more details on the console
        logger.setMessageOutputLevel(Project.MSG_INFO);
        project.fireBuildStarted();

        LOG.info(
                "Launched new web crawl session on new JVM. Action: '{}'. "
                        + (ArrayUtils.isEmpty(extraArgs) ? ""
                                : "Extra arguments: '"
                                        + join(" ", extraArgs) + "'."),
                action);
        Throwable caught = null;
        var retValue = 0;
        try {
            var javaTask = new Java();
            javaTask.setTaskName("runjava");
            javaTask.setProject(project);
            javaTask.setFork(true);
            javaTask.setFailonerror(true);
            javaTask.setClassname(WebCrawler.class.getName());

            var envVar = new Environment.Variable();
            envVar.setKey("CLASSPATH");
            envVar.setValue(SystemUtils.JAVA_CLASS_PATH);
            javaTask.addEnv(envVar);

            var args = action + " -config=\"" +
                    configFile.toAbsolutePath().toFile() + "\"";
            if (ArrayUtils.isNotEmpty(extraArgs)) {
                args += " " + join(" ", extraArgs);
            }
            javaTask.getCommandLine().createArgument().setLine(args);
            javaTask.init();
            LOG.info(
                    "Command: {}",
                    javaTask.getCommandLine().describeCommand());
            retValue = javaTask.executeJava();

            LOG.info("Done. Return code: {}", retValue);
        } catch (BuildException e) {
            caught = e;
            retValue = -1;
        }
        project.log("Finished");
        project.fireBuildFinished(caught);
        return retValue;
    }

    // Add a file-based committer to first crawler if none is present
    @SneakyThrows
    private static void addTestCommitterOnce(CrawlerConfig cfg) {
        if (cfg.getCommitters().isEmpty() || cfg.getCommitters()
                .stream().noneMatch(c -> c instanceof TestCommitter)) {
            var committer = new TestCommitter(
                    cfg.getWorkDir().resolve(committerDir));
            committer.init(null);
            List<Committer> committers = new ArrayList<>(cfg.getCommitters());
            committers.add(committer);
            cfg.setCommitters(committers);
        }
    }

    @Data
    @Setter(value = AccessLevel.NONE)
    public static class CrawlOutcome {
        final MemoryCommitter committerCombininedLaunches =
                new MemoryCommitter();
        final MemoryCommitter committerAfterLaunch =
                new MemoryCommitter();
        private final int returnValue;
        private final String stdOut;
        private final String stdErr;

        private CrawlOutcome(Captured<Integer> captured) {
            returnValue = captured.getReturnValue();
            stdOut = captured.getStdOut();
            stdErr = captured.getStdErr();
        }
    }
}
