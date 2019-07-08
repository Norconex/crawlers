/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.recovery;

import java.io.IOException;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.junit.jupiter.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.data.store.ICrawlDataStoreFactory;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core.impl.FileSystemCommitter;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.xml.XML;

/**
 * Test that the right amount of docs are crawled after crashing the JVM
 * and starting/resuming the collector.
 * @author Pascal Essiembre
 */
public abstract class AbstractTestJvmCrash
        extends AbstractInfiniteDepthTestFeature {

    private static final Logger LOG = LoggerFactory.getLogger(
            AbstractTestJvmCrash.class);

    protected abstract boolean isResuming();

    protected abstract ICrawlDataStoreFactory createCrawlDataStore();

    @Override
    public int numberOfRun() {
        return isResuming() ? 3 : 2;
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg)
            throws Exception {

        cfg.setStartURLs(cfg.getStartURLs().get(0) + "?depth=0");
        cfg.addEventListeners(new JVMCrasher());
        cfg.setCommitter(new FileSystemCommitter());
        cfg.setCrawlDataStoreFactory(createCrawlDataStore());

        if (isFirstRun()) {
            cfg.setMaxDocuments(10);
        } else if (isSecondRun() && isResuming()) {
            cfg.setMaxDocuments(10);
        } else {
            cfg.setMaxDocuments(5);
        }
    }

    @Override
    public void startCollector(HttpCollector collector) throws Exception {
        int exitValue = 0;

        // Crash the first crawl
        if (isFirstRun()) {
            LOG.info("Start and crash JVM test.");
            exitValue = runCollector("start", collector);
            Assertions.assertEquals(JVMCrasher.CRASH_EXIT_VALUE,
                    exitValue, "Wrong crash exit value.");

        // Resume previous crawl
        } else if (isSecondRun() && isResuming()) {
            LOG.info("Resume from JVM crash test.");
            exitValue = runCollector("resume", collector);
            Assertions.assertEquals(0, exitValue, "Wrong resume exit value.");

        // Recrawl without crash
        } else {
            LOG.info("Good full test run with no JVM crash.");
            exitValue = runCollector("start", collector);
            Assertions.assertEquals(0, exitValue, "Wrong start exit value.");
        }
    }

    private int runCollector(String action, HttpCollector collector)
            throws IOException, XMLStreamException {

        // Save collector config to local file
        HttpCollectorConfig cfg = collector.getCollectorConfig();
        Path workdir = cfg.getWorkDir();
        Path configFile = cfg.getWorkDir().resolve("config.xml");

        XML xml = new XML("httpcollector");
        cfg.saveToXML(xml);
        xml.write(configFile.toFile());

        Project project = new Project();
        project.setBaseDir(workdir.toFile());
        project.init();
        DefaultLogger logger = new DefaultLogger();
        project.addBuildListener(logger);
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        // Change to MSG_DEBUG to get more details on the console
        logger.setMessageOutputLevel(Project.MSG_INFO);
//        System.setOut(new PrintStream(new DemuxOutputStream(project, false)));
//        System.setErr(new PrintStream(new DemuxOutputStream(project, true)));
        project.fireBuildStarted();

        LOG.info("\"{}\" in new JVM.", action);
        Throwable caught = null;
        int retValue = 0;
        try {
            Java javaTask = new Java();
            javaTask.setTaskName("runjava");
            javaTask.setProject(project);
            javaTask.setFork(true);
            javaTask.setFailonerror(true);
            javaTask.setClassname(HttpCollector.class.getName());
            javaTask.setClasspath(new org.apache.tools.ant.types.Path(
                    project, SystemUtils.JAVA_CLASS_PATH));
            String args = "-a " + action
                    + " -c \"" + configFile.toAbsolutePath().toFile() + "\"";
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

    @Override
    public void test(HttpCollector collector) throws Exception {
        Path workdir = collector.getCollectorConfig().getWorkDir();
        FileSystemCommitter committer =
                (FileSystemCommitter) getCommitter(collector);
        Path committedDir = workdir.resolve(committer.getDirectory());

        if (isFirstRun()) {
            // JVMCrasher crashes after 7th *fetch*, so only 6 should have been
            // committed.
            Assertions.assertEquals(6, countAddedFiles(committedDir),
                    "Wrong number of committed files after JVM crash.");
        } else if (isSecondRun() && isResuming()) {
            // Should resume where left and reach 10 docs committed.
            Assertions.assertEquals(10, countAddedFiles(committedDir),
                    "Wrong number of committed files after resume.");
        } else {
            // Should run just fine after backup
            // Since we are not clearing previous committed files, 5 is added
            // to docs gathered so far.
            int expected = 11;
            if (isResuming()) {
                expected = 15;
            }
            Assertions.assertEquals(expected, countAddedFiles(committedDir),
                    "Wrong number of committed files after straight run.");
        }
    }



    private int countAddedFiles(Path dir) {
        return countFiles(dir, FileSystemCommitter.FILE_SUFFIX_ADD + ".ref");
    }
//    private int countDeletedFiles(Path dir) {
//        return countFiles(dir, FileSystemCommitter.FILE_SUFFIX_REMOVE + ".ref");
//    }
    private int countFiles(Path dir, String suffix) {
        final MutableInt count = new MutableInt();
        FileUtil.visitAllFiles(dir.toFile(), file -> count.increment(),
                FileFilterUtils.suffixFileFilter(suffix));
        return count.intValue();
    }
}
