package com.norconex.collector.http.crawler;

/* Copyright 2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.delay.impl.DefaultDelayResolver;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.website.TestWebServer;
import com.norconex.committer.impl.FileSystemCommitter;
import com.norconex.commons.lang.Content;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;

public abstract class AbstractHttpTest {

    static {
        // Root
        Logger logger = Logger.getRootLogger();
        logger.setLevel(Level.INFO);
        logger.setAdditivity(false);
        logger.addAppender(new ConsoleAppender(
                new PatternLayout("%-5p [%C{1}] %m%n"), 
                ConsoleAppender.SYSTEM_OUT));
        // Crawler
        logger = Logger.getLogger(HttpCrawler.class);
        logger.setLevel(Level.DEBUG);
        
    }
    
    private static final TestWebServer SERVER = new TestWebServer();

    //Note: @Rule was not working for deleting folder since the webapp 
    // still had a hold on the file.
    private static TemporaryFolder tempFolder = new TemporaryFolder();

    
    @BeforeClass
    public static void beforeClass() throws IOException {
        tempFolder.create();
        new Thread() {
            @Override
            public void run() {
                try {
                    SERVER.run();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }.start();
        while (SERVER.getLocalPort() <= 0) {
            Sleeper.sleepSeconds(1);
        }
    }

    @AfterClass
    public static void afterClass() throws Exception {
        SERVER.stop();
        FileUtil.delete(tempFolder.getRoot());
    }

    
    protected String getBaseUrl() {
        return "http://localhost:" + SERVER.getLocalPort();
    }
    protected String newUrl(String urlPath) {
        return getBaseUrl() + urlPath;
    }
//    protected File newTempFolder(String folderName) throws IOException {
//        return tempFolder.newFolder(folderName);
//    }
    protected File getCommitterAddDir(HttpCrawler crawler) {
        FileSystemCommitter committer = (FileSystemCommitter)
                crawler.getCrawlerConfig().getCommitter();
        return committer.getAddDir();
    }
    protected File getCommitterRemoveDir(HttpCrawler crawler) {
        FileSystemCommitter committer = (FileSystemCommitter)
                crawler.getCrawlerConfig().getCommitter();
        return committer.getRemoveDir();
    }
    
    protected List<HttpDocument> getCommitedDocuments(HttpCrawler crawler)
            throws IOException {
        File addDir = getCommitterAddDir(crawler);
        Collection<File> files = FileUtils.listFiles(addDir, null, true);
        List<HttpDocument> docs = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory() || file.getName().endsWith(".meta")) {
                continue;
            }
            HttpMetadata meta = new HttpMetadata(file.getAbsolutePath());
            meta.load(FileUtils.openInputStream(
                    new File(file.getAbsolutePath() + ".meta")));
            HttpDocument doc = new HttpDocument(
                    meta.getString(HttpMetadata.DOC_REFERENCE));
            // remove previous reference to avoid duplicates
            doc.getMetadata().remove(HttpMetadata.COLLECTOR_URL);
            doc.getMetadata().load(meta);
            doc.setContent(new Content(file));
            docs.add(doc);
        }
        return docs;
    }

    protected HttpCollector newHttpCollector1Crawler(String... startURLs) 
            throws IOException {
        
        File progressDir = tempFolder.newFolder("progress" + UUID.randomUUID());
        File logsDir = tempFolder.newFolder("logs" + UUID.randomUUID());
        File workdir = tempFolder.newFolder("workdir" + UUID.randomUUID());
        File committerDir = tempFolder.newFolder(
                "committedFiles" + UUID.randomUUID());
        
        //--- Committer ---
        //ICommitter committer = new NilCommitter();
        FileSystemCommitter committer = new FileSystemCommitter();
        committer.setDirectory(committerDir.getAbsolutePath());

        //--- Crawler ---
        HttpCrawlerConfig httpConfig = new HttpCrawlerConfig();
        httpConfig.setId("Unit Test HTTP Crawler instance "
                + UUID.randomUUID());
        String[] urls = new String[startURLs.length];
        for (int i = 0; i < startURLs.length; i++) {
            urls[i] = getBaseUrl() + startURLs[i];
        }
        httpConfig.setStartURLs(urls);
        httpConfig.setWorkDir(workdir);
        httpConfig.setNumThreads(1);
        DefaultDelayResolver resolver = new DefaultDelayResolver();
        resolver.setDefaultDelay(0);
        httpConfig.setDelayResolver(resolver);
        httpConfig.setCommitter(committer);
        HttpCrawler crawler = new HttpCrawler(httpConfig);
        
        //--- Collector ---
        HttpCollectorConfig colConfig = new HttpCollectorConfig();
        colConfig.setId("Unit Test HTTP Collector instance " 
                + UUID.randomUUID());
        colConfig.setProgressDir(progressDir.getAbsolutePath());
        colConfig.setLogsDir(logsDir.getAbsolutePath());
        HttpCollector collector = new HttpCollector(colConfig);
        collector.setCrawlers(new HttpCrawler[]{ crawler });
        return collector;
    }
}
