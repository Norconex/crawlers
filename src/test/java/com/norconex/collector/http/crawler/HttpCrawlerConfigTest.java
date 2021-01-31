/* Copyright 2015-2021 Norconex Inc.
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

import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.collector.http.fetch.impl.HttpAuthConfig;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.encrypt.EncryptionKey;
import com.norconex.commons.lang.xml.XML;

/**
 * @author Pascal Essiembre
 */
class HttpCrawlerConfigTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCrawlerConfigTest.class);


    @Test
    void testWriteRead() {
        HttpCollectorConfig config = new HttpCollectorConfig();

        XML xml = new ConfigurationLoader().loadXML(Paths.get(
                "src/site/resources/examples/complex/complex-config.xml"));
        xml.populate(config);

        HttpCrawlerConfig crawlerConfig =
                (HttpCrawlerConfig) config.getCrawlerConfigs().get(0);
        GenericHttpFetcher fetcher =
                (GenericHttpFetcher) crawlerConfig.getHttpFetchers().get(0);
        fetcher.getConfig().setRequestHeader("header1", "value1");
        fetcher.getConfig().setRequestHeader("header2", "value2");
        fetcher.getConfig().getProxySettings().getCredentials().setPasswordKey(
                new EncryptionKey("C:\\keys\\myEncryptionKey.txt",
                        EncryptionKey.Source.FILE));
        HttpAuthConfig authConfig = new HttpAuthConfig();
        authConfig.getCredentials().setPasswordKey(
                new EncryptionKey("my key"));

        fetcher.getConfig().setAuthConfig(authConfig);

        crawlerConfig.setStartURLsProviders(new MockStartURLsProvider());


        LOG.debug("Writing/Reading this: {}", config);
        XML.assertWriteRead(config, "httpcollector");
    }
}
