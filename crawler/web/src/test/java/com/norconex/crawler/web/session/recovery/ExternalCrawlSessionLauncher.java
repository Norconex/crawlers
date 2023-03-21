/* Copyright 2019-2023 Norconex Inc.
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
package com.norconex.crawler.web.session.recovery;

import static java.lang.String.join;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;

import com.norconex.committer.core.Committer;
import com.norconex.committer.core.CommitterContext;
import com.norconex.committer.core.CommitterException;
import com.norconex.committer.core.CommitterRequest;
import com.norconex.committer.core.DeleteRequest;
import com.norconex.committer.core.UpsertRequest;
import com.norconex.committer.core.batch.queue.impl.FSQueueUtil;
import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.SystemUtil;
import com.norconex.commons.lang.SystemUtil.Captured;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.session.CrawlSessionConfig;
import com.norconex.crawler.web.WebCrawlSession;
import com.norconex.crawler.web.WebTestUtil;

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

    public static CrawlOutcome start(CrawlSessionConfig sessionConfig) {
        return launch(sessionConfig, "start");
    }
    public static CrawlOutcome startClean(CrawlSessionConfig sessionConfig) {
        return launch(sessionConfig, "start -clean");
    }
    public static CrawlOutcome clean(CrawlSessionConfig sessionConfig) {
        return launch(sessionConfig, "clean");
    }

    public static CrawlOutcome launch(
            CrawlSessionConfig sessionConfig,
            String action,
            String... extraArgs) {
        // make sure things are not happening to fast for our tests
        //Sleeper.sleepMillis(3000);
        var now = ZonedDateTime.now();
        Captured<Integer> captured = SystemUtil.callAndCaptureOutput(
                () -> doLaunch(sessionConfig, action, extraArgs));
        var outcome = new CrawlOutcome(captured);
        var c = new TestCommitter(
                sessionConfig.getWorkDir().resolve(committerDir));
        c.fillMemoryCommitters(outcome, now);
        return outcome;
    }

    private static int doLaunch(
            CrawlSessionConfig sessionConfig,
            String action,
            String... extraArgs) {

        if ("start".equalsIgnoreCase(action)) {
            addTestCommitterOnce(sessionConfig);
        }

        // Save session config to file so it can be used from command-line
        var configFile = sessionConfig.getWorkDir().resolve("config.xml");
        var xml = new XML("crawlSession");
        sessionConfig.saveToXML(xml);
        xml.write(configFile.toFile());

        var project = new Project();
        project.setBaseDir(sessionConfig.getWorkDir().toFile());
        project.init();
        var logger = new DefaultLogger();
        project.addBuildListener(logger);
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        // Change to MSG_DEBUG to get more details on the console
        logger.setMessageOutputLevel(Project.MSG_INFO);
        project.fireBuildStarted();

        LOG.info("Launched new web crawl session on new JVM. Action: '{}'. "
                + (ArrayUtils.isEmpty(extraArgs) ? "" :  "Extra arguments: '"
                        + join(" ", extraArgs) + "'."), action);
        Throwable caught = null;
        var retValue = 0;
        try {
            var javaTask = new Java();
            javaTask.setTaskName("runjava");
            javaTask.setProject(project);
            javaTask.setFork(true);
            javaTask.setFailonerror(true);
            javaTask.setClassname(WebCrawlSession.class.getName());
            javaTask.setClasspath(new org.apache.tools.ant.types.Path(
                    project, SystemUtils.JAVA_CLASS_PATH));
            var args = action + " -config=\"" +
                    configFile.toAbsolutePath().toFile() + "\"";
            if (ArrayUtils.isNotEmpty(extraArgs)) {
                args += " " + join(" ", extraArgs);
            }
            javaTask.getCommandLine().createArgument().setLine(args);
            javaTask.init();
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
    private static void addTestCommitterOnce(CrawlSessionConfig sessionConfig) {
        var cfg = WebTestUtil.getFirstCrawlerConfig(sessionConfig);
        if (cfg.getCommitters().isEmpty() || cfg.getCommitters()
                .stream().noneMatch(c -> c instanceof TestCommitter)) {
            var committer = new TestCommitter(
                    sessionConfig.getWorkDir().resolve(committerDir));
            committer.init(null);
            List<Committer> committers = new ArrayList<>(cfg.getCommitters());
            committers.add(committer);
            cfg.setCommitters(committers);
        }
    }

    @Data
    @Setter(value = AccessLevel.NONE)
    public static class CrawlOutcome {
        private final MemoryCommitter committerCombininedLaunches =
                new MemoryCommitter();
        private final MemoryCommitter committerAfterLaunch =
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

    @Data
    public static class TestCommitter implements Committer, XMLConfigurable {
        private Path dir;
        public TestCommitter() {}
        public TestCommitter(Path dir) {
            this.dir = dir;
        }

        @Override
        @SneakyThrows
        public void init(CommitterContext committerContext)
                throws CommitterException {
            Files.createDirectories(dir);
        }
        @Override
        @SneakyThrows
        public void clean() throws CommitterException {
            FileUtil.delete(dir.toFile());
        }
        @Override
        public boolean accept(CommitterRequest request)
                throws CommitterException {
            return true;
        }
        @Override
        @SneakyThrows
        public void upsert(UpsertRequest upsertRequest)
                throws CommitterException {
            FSQueueUtil.toZipFile(upsertRequest, dir.resolve(
                    "upsert-" + UUID.randomUUID() + ".zip"));
        }
        @Override
        @SneakyThrows
        public void delete(DeleteRequest deleteRequest)
                throws CommitterException {
            FSQueueUtil.toZipFile(deleteRequest, dir.resolve(
                    "delete-" + UUID.randomUUID() + ".zip"));
        }
        @Override
        public void close() throws CommitterException {
            //NOOP
        }
        @Override
        public void loadFromXML(XML xml) {
            setDir(xml.getPath("dir", dir));
        }
        @Override
        public void saveToXML(XML xml) {
            xml.addElement("dir", dir);
        }
        @SneakyThrows
        public void fillMemoryCommitters(
                CrawlOutcome outcome, ZonedDateTime launchTime) {
            FSQueueUtil.findZipFiles(dir).forEach(zip -> {
                try {
                    CommitterRequest req = FSQueueUtil.fromZipFile(zip);
                    if (Files.getLastModifiedTime(zip).compareTo(
                            FileTime.from(launchTime.toInstant())) > 0) {
                        if (req instanceof UpsertRequest upsert) {
                            outcome.committerAfterLaunch.upsert(upsert);
                        } else {
                            outcome.committerAfterLaunch.delete(
                                    (DeleteRequest) req);
                        }
                    }
                    if (req instanceof UpsertRequest upsert) {
                        outcome.committerCombininedLaunches.upsert(upsert);
                    } else {
                        outcome.committerCombininedLaunches.delete(
                                (DeleteRequest) req);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
