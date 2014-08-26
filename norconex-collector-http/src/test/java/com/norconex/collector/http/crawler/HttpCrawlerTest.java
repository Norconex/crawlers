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
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.website.TestWebServer;
import com.norconex.committer.impl.FileSystemCommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;

public class HttpCrawlerTest {

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

    public String getBaseUrl() {
        return "http://localhost:" + SERVER.getLocalPort();
    }


    @Test
    public void testFeatures() throws IOException {
        File progressDir = tempFolder.newFolder("progress");
        File logsDir = tempFolder.newFolder("logs");
        File workdir = tempFolder.newFolder("workdir");
        File committerDir = tempFolder.newFolder("committedFiles");
        
        //--- Committer ---
        //ICommitter committer = new NilCommitter();
        FileSystemCommitter committer = new FileSystemCommitter();
        committer.setDirectory(committerDir.getAbsolutePath());

        //--- Crawler ---
        HttpCrawlerConfig httpConfig = new HttpCrawlerConfig();
        httpConfig.setId("Unit Test HTTP Crawler");
        httpConfig.setStartURLs(new String[]{
                getBaseUrl() + "/index.html"
        });
        httpConfig.setWorkDir(workdir);
        httpConfig.setNumThreads(1);
        httpConfig.setCommitter(committer);
        
        //--- Collector ---
        HttpCollectorConfig colConfig = new HttpCollectorConfig();
        colConfig.setId("Unit Test HTTP Collector");
        colConfig.setProgressDir(progressDir.getAbsolutePath());
        colConfig.setLogsDir(logsDir.getAbsolutePath());
        colConfig.setCrawlerConfigs(new HttpCrawlerConfig[]{ httpConfig });
        
        
        
        HttpCollector collector = new HttpCollector(colConfig);

        collector.start(false);
        
        System.out.println("committer dir: " + tempFolder.getRoot());
    }

    

}
