/* Copyright 2017-2020 Norconex Inc.
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

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.committer.core3.fs.impl.JSONFileCommitter;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLValidationException;


/**
 * @author Pascal Essiembre
 */
public class HttpCollectorConfigTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCollectorConfigTest.class);

    @Test
    public void testWriteRead() {
        HttpCollectorConfig config = new HttpCollectorConfig();
        config.setId("test-http-collector");

        HttpCrawlerConfig crawlerCfg = new HttpCrawlerConfig();
        crawlerCfg.setId("test-http-crawler");
        crawlerCfg.setCommitter(new JSONFileCommitter());
        crawlerCfg.setStartURLs(
                "http://www.example.com/1/", "http://www.example.com/2/");
        crawlerCfg.setStartURLsFiles(
                Paths.get("/path/file1.txt"), Paths.get("/path/file2.txt"));
        config.setCrawlerConfigs(new CrawlerConfig[] {crawlerCfg});

        LOG.debug("Writing/Reading this: {}", config);
        XML.assertWriteRead(config, "httpcollector");
    }


    @Test
    public void testValidation() throws IOException {
        try (Reader r = new InputStreamReader(getClass().getResourceAsStream(
                "/validation/collector-http-full.xml"))) {
            new HttpCollectorConfig().loadFromXML(new XML(r));
        } catch (XMLValidationException e) {
            Assertions.fail(e.getErrors().size()
                    + "Validation warnings/errors were found.");
        }
    }

    // Test for: https://github.com/Norconex/collector-http/issues/326
    @Test
    public void testCrawlerDefaults() throws IOException {
        HttpCollectorConfig config = TestUtil.loadCollectorConfig(getClass());
        Assertions.assertEquals(2, config.getCrawlerConfigs().size());

        // Make sure crawler defaults are applied properly.
        HttpCrawlerConfig cc1 =
                (HttpCrawlerConfig) config.getCrawlerConfigs().get(0);
        Assertions.assertFalse(
                cc1.getURLCrawlScopeStrategy().isStayOnDomain(),
                "stayOnDomain 1 must be false");
        Assertions.assertFalse(
                cc1.getURLCrawlScopeStrategy().isStayOnPort(),
                "stayOnPort 1 must be false");
        Assertions.assertTrue(
                cc1.getURLCrawlScopeStrategy().isStayOnProtocol(),
                "stayOnProtocol 1 must be true");

        HttpCrawlerConfig cc2 =
                (HttpCrawlerConfig) config.getCrawlerConfigs().get(1);
        Assertions.assertTrue(
                cc2.getURLCrawlScopeStrategy().isStayOnDomain(),
                "stayOnDomain 2 must be true");
        Assertions.assertTrue(
                cc2.getURLCrawlScopeStrategy().isStayOnPort(),
                "stayOnPort 2 must be true");
        Assertions.assertTrue(
                cc2.getURLCrawlScopeStrategy().isStayOnProtocol(),
                "stayOnProtocol 2 must be true");
    }
}
