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

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.website.TestWebServer;
import com.norconex.committer.impl.FileSystemCommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;

public abstract class AbstractHttpTest {

    static {
        Logger logger = Logger.getRootLogger();
        logger.setLevel(Level.INFO);
        logger.setAdditivity(false);
        logger.addAppender(new ConsoleAppender(
                new PatternLayout("%-5p [%C{1}] %m%n"), 
                ConsoleAppender.SYSTEM_OUT));
    }
    
    private static final TestWebServer SERVER = new TestWebServer();

    //Note: @Rule was not working for deleting folder since the webapp 
    // still had a hold on the file.
    public static TemporaryFolder tempFolder = new TemporaryFolder();

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
    protected File newTempFolder(String folderName) throws IOException {
        return tempFolder.newFolder(folderName);
    }
    protected File getCommitterAddDir(HttpCollector collector) {
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        FileSystemCommitter committer = (FileSystemCommitter)
                crawler.getCrawlerConfig().getCommitter();
        return committer.getAddDir();
    }
    protected File getCommitterRemoveDir(HttpCollector collector) {
        HttpCrawler crawler = (HttpCrawler) collector.getCrawlers()[0];
        FileSystemCommitter committer = (FileSystemCommitter)
                crawler.getCrawlerConfig().getCommitter();
        return committer.getRemoveDir();
    }
    
    

    protected HttpCollector newHttpCollector1Crawler(String... startURLs) 
            throws IOException {
        File progressDir = newTempFolder("progress");
        File logsDir = newTempFolder("logs");
        File workdir = newTempFolder("workdir");
        File committerDir = newTempFolder("committedFiles");
        
        //--- Committer ---
        //ICommitter committer = new NilCommitter();
        FileSystemCommitter committer = new FileSystemCommitter();
        committer.setDirectory(committerDir.getAbsolutePath());

        //--- Crawler ---
        HttpCrawlerConfig httpConfig = new HttpCrawlerConfig();
        httpConfig.setId("Unit Test HTTP Crawler");
        String[] urls = new String[startURLs.length];
        for (int i = 0; i < startURLs.length; i++) {
            urls[i] = getBaseUrl() + startURLs[i];
        }
        httpConfig.setStartURLs(urls);
        httpConfig.setWorkDir(workdir);
        httpConfig.setNumThreads(1);
        httpConfig.setCommitter(committer);
        HttpCrawler crawler = new HttpCrawler(httpConfig);
        
        //--- Collector ---
        HttpCollectorConfig colConfig = new HttpCollectorConfig();
        colConfig.setId("Unit Test HTTP Collector");
        colConfig.setProgressDir(progressDir.getAbsolutePath());
        colConfig.setLogsDir(logsDir.getAbsolutePath());
        HttpCollector collector = new HttpCollector(colConfig);
        collector.setCrawlers(new HttpCrawler[]{ crawler });
        return collector;
    }

    
}
