/* Copyright 2017-2018 Norconex Inc.
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
package com.norconex.collector.http;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.file.Paths;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.ICrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.committer.core.impl.FileSystemCommitter;
import com.norconex.commons.lang.xml.XML;


/**
 * @author Pascal Essiembre
 */
public class HttpCollectorConfigTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCollectorConfigTest.class);

    @Test
    public void testWriteRead() throws IOException {
        HttpCollectorConfig config = new HttpCollectorConfig();
        config.setId("test-http-collector");

        HttpCrawlerConfig crawlerCfg = new HttpCrawlerConfig();
        crawlerCfg.setId("test-http-crawler");
        crawlerCfg.setCommitter(new FileSystemCommitter());
        crawlerCfg.setStartURLs(
                "http://www.example.com/1/", "http://www.example.com/2/");
        crawlerCfg.setStartURLsFiles(
                Paths.get("/path/file1.txt"), Paths.get("/path/file2.txt"));
        config.setCrawlerConfigs(new ICrawlerConfig[] {crawlerCfg});

        LOG.debug("Writing/Reading this: {}", config);
        XML.assertWriteRead(config, "httpcollector");
    }


    @Test
    public void testValidation() throws IOException {
//        CountingConsoleAppender appender = new CountingConsoleAppender();
//        appender.startCountingFor(XMLConfigurationUtil.class, Level.WARN);
//        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
//                "/validation/collector-http-full.xml"))) {
//            XMLConfigurationUtil.loadFromXML(
//                    new HttpCollectorConfig(), r);
//        } finally {
//            appender.stopCountingFor(XMLConfigurationUtil.class);
//        }
//        Assert.assertEquals("Validation warnings/errors were found.",
//                0, appender.getCount());

          try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                  "/validation/collector-http-full.xml"))) {
              Assert.assertTrue("Validation warnings/errors were found.",
                      new XML(r).validate(HttpCollectorConfig.class).isEmpty());
          }
    }

    // Test for: https://github.com/Norconex/collector-http/issues/326
    @Test
    public void testCrawlerDefaults() throws IOException {
        HttpCollectorConfig config = TestUtil.loadCollectorConfig(getClass());
        Assert.assertEquals(2, config.getCrawlerConfigs().size());

        // Make sure crawler defaults are applied properly.
        HttpCrawlerConfig cc1 =
                (HttpCrawlerConfig) config.getCrawlerConfigs().get(0);
        Assert.assertFalse("stayOnDomain 1 must be false",
                cc1.getURLCrawlScopeStrategy().isStayOnDomain());
        Assert.assertFalse("stayOnPort 1 must be false",
                cc1.getURLCrawlScopeStrategy().isStayOnPort());
        Assert.assertTrue("stayOnProtocol 1 must be true",
                cc1.getURLCrawlScopeStrategy().isStayOnProtocol());

        HttpCrawlerConfig cc2 =
                (HttpCrawlerConfig) config.getCrawlerConfigs().get(1);
        Assert.assertTrue("stayOnDomain 2 must be true",
                cc2.getURLCrawlScopeStrategy().isStayOnDomain());
        Assert.assertTrue("stayOnPort 2 must be true",
                cc2.getURLCrawlScopeStrategy().isStayOnPort());
        Assert.assertTrue("stayOnProtocol 2 must be true",
                cc2.getURLCrawlScopeStrategy().isStayOnProtocol());
    }
}
