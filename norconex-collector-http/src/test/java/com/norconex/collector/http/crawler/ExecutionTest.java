/* Copyright 2015-2019 Norconex Inc.
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
package com.norconex.collector.http.crawler;

import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Path;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.collector.core.data.store.ICrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.mvstore.MVStoreCrawlDataStoreFactory;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.data.store.impl.jdbc.JDBCCrawlDataStoreFactory;
import com.norconex.committer.core.impl.FileSystemCommitter;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.map.Properties;

/**
 * @author Pascal Essiembre
 *
 */
public class ExecutionTest extends AbstractHttpTest {

    /**
     * By default recovery tests are disabled as they rely on execution
     * time of different processes which is not predictable enough
     * to prevent false failures (test[Resume|Start]... methods).
     * It is recommended you enable those in development but otherwise leave
     * them disable as it will often break your automated build process.
     * Should be revisited if we find a way to make them more stable.
     * To enable, set the system property "enableRecoveryTests" to "true".
     */
    public static final boolean ENABLE_RECOVERY_TESTS =
            Boolean.getBoolean("enableRecoveryTests");

    private Path workDir;
    private Path committedDir;
    private Path progressDir;
    private Path varsFile;
    private Path configFile;;
    private Properties vars;

//    /**
//     * Constructor.
//     */
//    public ExecutionTest() {
//    }
//
//    @BeforeAll
//    public static void notifyOfRecoveryTests() {
//        if (!ENABLE_RECOVERY_TESTS) {
//            System.out.println("Recovery tests are disabled in "
//                    + ExecutionTest.class.getCanonicalName()
//                    + ". To enable them, set -DenableRecoveryTests=true");
//        } else {
//            System.out.println("Recovery tests are enabled in "
//                    + ExecutionTest.class.getCanonicalName() + ".");
//        }
//    }
//
//    @BeforeEach
//    public void setup() throws IOException {
//        workDir = getTempFolder();
//        committedDir = workDir.resolve("committed");
//        progressDir = workDir.resolve("progress");
//        varsFile = workDir.resolve("test.properties");
//        configFile = workDir.resolve("test.cfg");
//
//        vars = new Properties();
//        vars.set("startURL", newUrl("/test?case=basic&amp;depth=0"));
//        vars.set("workDir", workDir);
//        vars.set("maxDepth", 10);
//        vars.set("maxDocuments", 10);
//        vars.set("delay", 0);
//    }
//    @AfterEach
//    public void tearDown() throws IOException {
//        FileUtil.delete(varsFile.toFile());
//        FileUtil.delete(configFile.toFile());
//        FileUtil.delete(workDir.toFile());
//        vars.clear();
//        workDir = null;
//        committedDir = null;
//        progressDir = null;
//        vars = null;
//        varsFile = null;
//        configFile = null;
//    }

    @Test
    public void testStartAfterJvmCrash()
            throws IOException, XMLStreamException {
        testAfterJvmCrash(false, MVStoreCrawlDataStoreFactory.class, null);
    }

    @Test
    public void testResumeAfterJvmCrash_MVStore()
            throws IOException, XMLStreamException {
        testAfterJvmCrash(true, MVStoreCrawlDataStoreFactory.class, null);
    }
    @Test
    public void testResumeAfterJvmCrash_Derby()
            throws IOException, XMLStreamException {
        testAfterJvmCrash(true, JDBCCrawlDataStoreFactory.class, "derby");
    }
    @Test
    public void testResumeAfterJvmCrash_H2()
            throws IOException, XMLStreamException {
        testAfterJvmCrash(true, JDBCCrawlDataStoreFactory.class, "h2");
    }

    private void testAfterJvmCrash(
            boolean resume,
            Class<? extends ICrawlDataStoreFactory> storeFactory,
            String database)  throws IOException, XMLStreamException {

        if (!ENABLE_RECOVERY_TESTS) {
            return;
        }

        vars.set("crawlerListener", JVMCrasher.class);
        vars.set("crawlDataStoreFactory", storeFactory);
        if (database != null) {
            vars.set("crawlDataStoreFactoryDatabase", database);
        }

        int exitValue = 0;

        //--- Crash start run ---
        System.out.println("\n--- Crash start run ---");
        exitValue = runCollector("start", vars);
        Assertions.assertEquals(
                JVMCrasher.CRASH_EXIT_VALUE, exitValue,
                "Wrong crash exit value.");
        // JVMCrasher crashes after 7th *fetch*, so only 6 should have been
        // committed.
        Assertions.assertEquals(
                6, countAddedFiles(),
                "Wrong number of committed files after JVM crash.");
        ageProgress(progressDir);


        //--- Resume run ---
        if (resume) {
            // Should resume where left and reach 10 docs committed.
            System.out.println("\n--- Resume run ---");
            exitValue = runCollector("resume", vars);

            Assertions.assertEquals( 0, exitValue,
                "Wrong resume exit value.");
            Assertions.assertEquals(
                    10, countAddedFiles(),
                "Wrong number of committed files after resume.");
            ageProgress(progressDir);
        }

        //--- Good start run ---
        // Should run just fine after backup
        System.out.println("\n--- Good start run ---");
        vars.set("maxDocuments", 5);
        exitValue = runCollector("start", vars);
        Assertions.assertEquals( 0, exitValue,
                "Wrong start exit value.");
        // Since we are not clearing previous committed files, 5 is added
        // to docs gathered so far.
        int expected = 11;
        if (resume) {
            expected = 15;
        }
        Assertions.assertEquals(
                expected, countAddedFiles(),

                "Wrong number of committed files after straight run.");
        ageProgress(progressDir);
    }

    private int countAddedFiles() {
        return countFiles(committedDir,
                FileSystemCommitter.FILE_SUFFIX_ADD + ".ref");
    }
    private int countDeletedFiles() {
        return countFiles(committedDir,
                FileSystemCommitter.FILE_SUFFIX_REMOVE + ".ref");
    }
    private int countFiles(Path dir, String suffix) {
        final MutableInt count = new MutableInt();
        FileUtil.visitAllFiles(dir.toFile(), file -> count.increment(),
                FileFilterUtils.suffixFileFilter(suffix));
        return count.intValue();
    }

    // Age progress files to fool activity tracker so we can restart right away.
    private void ageProgress(Path progressDir) {
        final long age = System.currentTimeMillis() - (10 * 1000);
        FileUtil.visitAllFiles(
                progressDir.toFile(), file -> file.setLastModified(age));
    }

    private int runCollector(String action, Properties configVars)
            throws IOException, XMLStreamException {

        // Config + variables
        if (configVars != null) {
            try (Writer w = new FileWriter(varsFile.toFile())) {
                configVars.storeToProperties(w, "");
            }
            try (InputStream is = getClass().getResourceAsStream(
                    "ExecutionTest-config.xml")) {
                FileUtils.copyInputStreamToFile(is, configFile.toFile());
            }
        }

        Project project = new Project();
//        project.setBaseDir(getTempFolder().getRoot().toFile());
        project.init();
        DefaultLogger logger = new DefaultLogger();
        project.addBuildListener(logger);
        logger.setOutputPrintStream(System.out);
        logger.setErrorPrintStream(System.err);
        // Change to MSG_INFO to get more details on the console
        logger.setMessageOutputLevel(Project.MSG_DEBUG);
//        System.setOut(new PrintStream(new DemuxOutputStream(project, false)));
//        System.setErr(new PrintStream(new DemuxOutputStream(project, true)));
        project.fireBuildStarted();

        System.out.println("\"" + action + "\" in new JVM.");
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
                    + " -c \"" + configFile.toAbsolutePath().toFile()
                    + "\" -v \"" + varsFile + "\"";
            javaTask.getCommandLine().createArgument().setLine(args);
            javaTask.init();
            retValue = javaTask.executeJava();
            System.out.println("Done. Return code: " + retValue);

        } catch (BuildException e) {
            caught = e;
            retValue = -1;
        }
        project.log("Finished");
        project.fireBuildFinished(caught);

        return retValue;
    }
}
