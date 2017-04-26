/* Copyright 2015 Norconex Inc.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;

import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.DefaultLogger;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Java;
import org.apache.tools.ant.types.Path;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.norconex.collector.core.checksum.impl.MD5DocumentChecksummer;
import com.norconex.collector.core.data.store.ICrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.mvstore.MVStoreCrawlDataStoreFactory;
import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.checksum.impl.LastModifiedMetadataChecksummer;
import com.norconex.collector.http.data.store.impl.jdbc.JDBCCrawlDataStoreFactory;
import com.norconex.committer.core.impl.FileSystemCommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.commons.lang.file.IFileVisitor;
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
    
    private File workDir;
    private File committedDir;
    private File progressDir;
    private File varsFile;
    private File configFile;;
    private Properties vars;
    
    /**
     * Constructor.
     */
    public ExecutionTest() {
    }

    @BeforeClass
    public static void notifyOfRecoveryTests() {
        if (!ENABLE_RECOVERY_TESTS) {
            System.out.println("Recovery tests are disabled in "
                    + ExecutionTest.class.getCanonicalName()
                    + ". To enable them, set -DenableRecoveryTests=true");
        } else {
            System.out.println("Recovery tests are enabled in "
                    + ExecutionTest.class.getCanonicalName() + ".");
        }
    }
    
    @Before
    public void setup() throws IOException {
        workDir = getTempFolder().newFolder();
        committedDir = new File(workDir, "committed");
        progressDir = new File(workDir, "progress");
        varsFile = getTempFolder().newFile("test.properties");
        configFile = getTempFolder().newFile("test.cfg");
        
        vars = new Properties();
        vars.setString("startURL", newUrl("/test?case=basic&amp;depth=0"));
        vars.setFile("workDir", workDir);
        vars.setInt("maxDepth", 10);
        vars.setInt("maxDocuments", 10);
        vars.setInt("delay", 0);
    }
    @After
    public void tearDown() throws IOException {
        FileUtil.delete(varsFile);
        FileUtil.delete(configFile);
        FileUtil.delete(workDir);
        vars.clear();
        workDir = null;
        committedDir = null;
        progressDir = null;
        vars = null;
        varsFile = null;
        configFile = null;
    }

    @Test
    public void testWebPageModificationDetection() 
            throws IOException, XMLStreamException {
        String startURL = newUrl("/test?case=modifiedFiles");
        vars.setString("startURL", startURL);
        vars.setClass(
                "metadataChecksummer", LastModifiedMetadataChecksummer.class);
        vars.setClass("documentChecksummer", MD5DocumentChecksummer.class);
        
        int exitValue = 0;

        // Test once and make sure we get 4 additions in total.
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong exit value.", 0, exitValue);
        Assert.assertEquals("Wrong number of added files.",
                4, countAddedFiles());
        ageProgress(progressDir);
        FileUtil.delete(committedDir);
        
        // Test twice and make sure we get 1 add (3 unmodified), because:
        // Page 1 has new modified date, we check content. Content is same.
        // Page 2 has same modified date, we do not go further (ignore content).
        // Page 3 has new modified date, so we check content. 
        // Content is modified.
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong exit value.", 0, exitValue);
        Assert.assertEquals("Wrong number of modified files.",
                1, countAddedFiles());
        ageProgress(progressDir);
        FileUtil.delete(committedDir);
        
        //TODO test with just header checksum, then with just content checksum?
    } 
    
    @Test
    public void testWebPageDeletionDetection() 
            throws IOException, XMLStreamException {
        String startURL = newUrl("/test?case=deletedFiles&amp;token="
                + System.currentTimeMillis());
        vars.setString("startURL", startURL);
        vars.setClass(
                "metadataChecksummer", LastModifiedMetadataChecksummer.class);
        vars.setClass("documentChecksummer", MD5DocumentChecksummer.class);
        
        int exitValue = 0;

        // Test once and make sure we get 4 additions in total.
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong exit value.", 0, exitValue);
        Assert.assertEquals("Wrong number of added files.",
                4, countAddedFiles());
        Assert.assertEquals("Wrong number of deleted files.",
                0, countDeletedFiles());
        ageProgress(progressDir);
        FileUtil.delete(committedDir);
        
        // Test twice and make sure we get 0 add (1 unmodified)
        // and 3 pages to delete.
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong exit value.", 0, exitValue);
        Assert.assertEquals("Wrong number of added files.",
                0, countAddedFiles());
        Assert.assertEquals("Wrong number of deleted files.",
                3, countDeletedFiles());
        ageProgress(progressDir);
        FileUtil.delete(committedDir);

        // Test a third time and make sure we get 0 add (1 unmodified)
        // and 3 new pages.
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong exit value.", 0, exitValue);
        Assert.assertEquals("Wrong number of added files.",
                3, countAddedFiles());
        Assert.assertEquals("Wrong number of deleted files.",
                0, countDeletedFiles());
    }

    //Test for https://github.com/Norconex/collector-http/issues/316
    @Test
    public void testWebPageTimeout() 
            throws IOException, XMLStreamException {
        String startURL = newUrl("/test?case=timeout&amp;token="
                + System.currentTimeMillis());
        vars.setString("startURL", startURL);
        vars.setClass("documentChecksummer", MD5DocumentChecksummer.class);
        vars.setString("extraCrawlerConfig", 
                "<httpClientFactory>"
              + "<connectionTimeout>2000</connectionTimeout>"
              + "<socketTimeout>2000</socketTimeout>"
              + "<connectionRequestTimeout>2000</connectionRequestTimeout>"
              + "</httpClientFactory>"
        );
        
        int exitValue = 0;

        // Test once and make sure we get 3 additions in total.
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong exit value.", 0, exitValue);
        Assert.assertEquals("Wrong number of added files.",
                3, countAddedFiles());
        ageProgress(progressDir);
        FileUtil.delete(committedDir);
        
        // Test twice and make sure we get 2 modified child docs even if
        // master times out (as opposed to consider child as orphans to be
        // deleted.
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong exit value.", 0, exitValue);
        Assert.assertEquals("Wrong number of modified files.",
                2, countAddedFiles());
        ageProgress(progressDir);
        FileUtil.delete(committedDir);
    } 
    
    @Test
    public void testStartAfterStopped()
            throws IOException, XMLStreamException, InterruptedException {
        testAfterStopped(false);
    }

    @Test
    public void testResumeAfterStopped()
            throws IOException, XMLStreamException, InterruptedException {
        testAfterStopped(true);
    }
    private void testAfterStopped(boolean resume)
            throws IOException, XMLStreamException, InterruptedException {
        
        if (!ENABLE_RECOVERY_TESTS) {
            return;
        }
        
        vars.setInt("delay", 5000);
        
        Thread newCrawl = new Thread() {
            @Override
            public void run() {
                try {
                    System.out.println("Starting collector.");
                    int returnValue = runCollector("start", vars);
                    Assert.assertEquals("Wrong first return value.", 0, 
                            returnValue); 
                } catch (IOException | XMLStreamException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        newCrawl.start();
        Sleeper.sleepSeconds(10);

        System.out.println("Requesting collector to stop.");
        int returnValue = runCollector("stop", null);
        Assert.assertEquals("Wrong stop return value.", 0, returnValue); 
        
        newCrawl.join();

        int fileCount = countAddedFiles();
        Assert.assertTrue("Should not have had time to process more than "
                + "2 or 3 files (processed " + fileCount + ").",
                fileCount > 1 && fileCount < 4);

        ageProgress(progressDir);
        vars.setInt("delay", 0);
        
        //--- Resume after stop ---
        if (resume) {
            int exitValue = runCollector("resume", vars);
            Assert.assertEquals("Wrong exit value.", 0, exitValue);
            Assert.assertEquals("Wrong number of committed files after resume.",
                    10, countAddedFiles());
        //--- Start after stop ---
        } else {
            FileUtil.delete(committedDir);
            vars.setInt("maxDocuments", 2);
            int exitValue = runCollector("start", vars);
            Assert.assertEquals("Wrong exit value.", 0, exitValue);
            Assert.assertEquals("Wrong number of committed files after start.",
                    2, countAddedFiles());
        }
    }

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
        
        vars.setClass("crawlerListener", JVMCrasher.class);
        vars.setClass("crawlDataStoreFactory", storeFactory);
        if (database != null) {
            vars.setString("crawlDataStoreFactoryDatabase", database);
        }
        
        int exitValue = 0;

        //--- Crash start run ---
        System.out.println("\n--- Crash start run ---");
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong crash exit value.", 
                JVMCrasher.CRASH_EXIT_VALUE, exitValue);
        // JVMCrasher crashes after 7th *fetch*, so only 6 should have been
        // committed.
        Assert.assertEquals("Wrong number of committed files after JVM crash.",
                6, countAddedFiles());
        ageProgress(progressDir);

        
        //--- Resume run ---
        if (resume) {
            // Should resume where left and reach 10 docs committed.
            System.out.println("\n--- Resume run ---");
            exitValue = runCollector("resume", vars);
            
            Assert.assertEquals("Wrong resume exit value.", 0, exitValue);
            Assert.assertEquals("Wrong number of committed files after resume.",
                    10, countAddedFiles());
            ageProgress(progressDir);
        }
        
        //--- Good start run ---
        // Should run just fine after backup
        System.out.println("\n--- Good start run ---");
        vars.setInt("maxDocuments", 5);
        exitValue = runCollector("start", vars);
        Assert.assertEquals("Wrong start exit value.", 0, exitValue);
        // Since we are not clearing previous committed files, 5 is added
        // to docs gathered so far.
        int expected = 11;
        if (resume) {
            expected = 15;
        }
        Assert.assertEquals(
                "Wrong number of committed files after straight run.",
                expected, countAddedFiles());
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
    private int countFiles(File dir, String suffix) {
        final MutableInt count = new MutableInt();
        FileUtil.visitAllFiles(dir, new IFileVisitor() {
            @Override
            public void visit(File file) {
                count.increment();
            }
        }, FileFilterUtils.suffixFileFilter(suffix));
        return count.intValue();
    }
    
    // Age progress files to fool activity tracker so we can restart right away.
    private void ageProgress(File progressDir) {
        final long age = System.currentTimeMillis() - (10 * 1000);
        FileUtil.visitAllFiles(progressDir, new IFileVisitor() {
            @Override
            public void visit(File file) {
                file.setLastModified(age);
            }
        });
    }
    
    private int runCollector(String action, Properties configVars)
            throws IOException, XMLStreamException {

        // Config + variables
        if (configVars != null) {
            try (Writer w = new FileWriter(varsFile)) {
                configVars.store(w, "");
            }
            try (InputStream is = getClass().getResourceAsStream(
                    "ExecutionTest-config.xml")) {
                FileUtils.copyInputStreamToFile(is, configFile);
            }
        }
        
        Project project = new Project();
        project.setBaseDir(getTempFolder().getRoot());
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
            javaTask.setClasspath(
                    new Path(project, SystemUtils.JAVA_CLASS_PATH));
            String args = "-a " + action
                    + " -c \"" + configFile.getAbsolutePath() 
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
