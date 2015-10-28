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
import java.io.IOException;

import org.junit.Test;

import com.norconex.collector.core.CollectorConfigLoader;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.client.impl.GenericHttpClientFactory;
import com.norconex.commons.lang.config.ConfigurationUtil;

/**
 * @author Pascal Essiembre
 */
public class HttpCrawlerConfigTest {

    
    @Test
    public void testWriteRead() throws IOException {
        File configFile = new File(
//                "src/site/resources/examples/minimum/minimum-config.xml");
                "src/site/resources/examples/complex/complex-config.xml");
        HttpCollectorConfig config = (HttpCollectorConfig) 
                new CollectorConfigLoader(HttpCollectorConfig.class)
                        .loadCollectorConfig(configFile);
        HttpCrawlerConfig crawlerConfig = 
                (HttpCrawlerConfig) config.getCrawlerConfigs()[0];
        GenericHttpClientFactory clientFactory = 
                (GenericHttpClientFactory) crawlerConfig.getHttpClientFactory();
        clientFactory.setRequestHeader("header1", "value1");
        clientFactory.setRequestHeader("header2", "value2");
        
        System.out.println("Writing/Reading this: " + config);
        ConfigurationUtil.assertWriteRead(config);
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
