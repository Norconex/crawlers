/* Copyright 2015-2018 Norconex Inc.
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

import java.io.IOException;
import java.nio.file.Paths;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.fetch.impl.GenericHttpFetcher;
import com.norconex.commons.lang.config.ConfigurationLoader;
import com.norconex.commons.lang.encrypt.EncryptionKey;
import com.norconex.commons.lang.xml.XML;

/**
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfigTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCrawlerConfigTest.class);

    @Test
    public void testWriteRead() throws IOException {
//        File configFile = new File(
////                "src/site/resources/examples/minimum/minimum-config.xml");
//                "src/site/resources/examples/complex/complex-config.xml");
//        HttpCollectorConfig config = (HttpCollectorConfig)
//                new CollectorConfigLoader(HttpCollectorConfig.class)
//                        .loadCollectorConfig(configFile);

        HttpCollectorConfig config = new HttpCollectorConfig();

        XML xml = new ConfigurationLoader().loadXML(Paths.get(
                "src/site/resources/examples/complex/complex-config.xml"));
        xml.configure(config);
//        new XML(new ConfigurationLoader().loadXML(
//                ).configure(config);

        HttpCrawlerConfig crawlerConfig =
                (HttpCrawlerConfig) config.getCrawlerConfigs().get(0);
        GenericHttpFetcher fetcher =
                (GenericHttpFetcher) crawlerConfig.getHttpFetchers().get(0);
        fetcher.getConfig().setRequestHeader("header1", "value1");
        fetcher.getConfig().setRequestHeader("header2", "value2");
        fetcher.getConfig().setProxyPasswordKey(new EncryptionKey(
                "C:\\keys\\myEncryptionKey.txt", EncryptionKey.Source.FILE));
        fetcher.getConfig().setAuthPasswordKey(new EncryptionKey("my key"));

        crawlerConfig.setStartURLsProviders(new MockStartURLsProvider());


        LOG.debug("Writing/Reading this: {}", config);
        XML.assertWriteRead(config, "httpcollector");
//        assertWriteRead(config);
    }


//    public static void assertWriteRead(IXMLConfigurable xmlConfiurable)
//            throws IOException {
//
//        // Write
//        Writer out = new OutputStreamWriter(System.out);
//        try {
//            xmlConfiurable.saveToXML(out);
//        } finally {
//            out.close();
//        }
//    }

}
