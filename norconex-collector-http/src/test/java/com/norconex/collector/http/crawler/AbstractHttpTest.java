/* Copyright 2014-2017 Norconex Inc.
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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.collector.http.doc.HttpDocument;
import com.norconex.collector.http.doc.HttpMetadata;
import com.norconex.collector.http.website.TestWebServer;
import com.norconex.committer.core.impl.FileSystemCommitter;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;

public abstract class AbstractHttpTest {

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
        // Give it a bit of time to warm up.
        Sleeper.sleepSeconds(5);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        SERVER.stop();
        FileUtil.delete(tempFolder.getRoot());
    }

    protected static TemporaryFolder getTempFolder() {
        return tempFolder;
    }
    
    protected String getBaseUrl() {
        return "http://localhost:" + SERVER.getLocalPort();
    }
    protected String newUrl(String urlPath) {
        return getBaseUrl() + urlPath;
    }
    protected File getCommitterDir(HttpCrawler crawler) {
        FileSystemCommitter committer = (FileSystemCommitter)
                crawler.getCrawlerConfig().getCommitter();
        File dir = new File(committer.getDirectory());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
    
    protected List<HttpDocument> getCommitedDocuments(HttpCrawler crawler)
            throws IOException {
        File addDir =  getCommitterDir(crawler);
        Collection<File> files = FileUtils.listFiles(addDir, null, true);
        List<HttpDocument> docs = new ArrayList<>();
        for (File file : files) {
            if (file.isDirectory() || !file.getName().endsWith(
                    FileSystemCommitter.EXTENSION_CONTENT)) {
                continue;
            }
            HttpMetadata meta = new HttpMetadata(file.getAbsolutePath());
            String basePath = StringUtils.removeEnd(
                    file.getAbsolutePath(), 
                    FileSystemCommitter.EXTENSION_CONTENT);
            meta.load(FileUtils.openInputStream(
                    new File(basePath + ".meta")));
            String reference = FileUtils.readFileToString(
                    new File(basePath + ".ref"), CharEncoding.UTF_8);
            
            HttpDocument doc = new HttpDocument(
                    reference, crawler.getStreamFactory().newInputStream(file));
            // remove previous reference to avoid duplicates
            doc.getMetadata().remove(HttpMetadata.COLLECTOR_URL);
            doc.getMetadata().load(meta);
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
                "committedFiles_" + UUID.randomUUID());
        
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
        GenericDelayResolver resolver = new GenericDelayResolver();
        resolver.setDefaultDelay(0);
        httpConfig.setDelayResolver(resolver);
        httpConfig.setIgnoreRobotsMeta(true);
        httpConfig.setIgnoreSitemap(true);
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
