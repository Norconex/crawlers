/* Copyright 2017 Norconex Inc.
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

import static org.junit.Assert.assertTrue;

import java.io.IOException;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import com.norconex.collector.core.ICollector;
import com.norconex.collector.core.ICollectorLifeCycleListener;
import com.norconex.collector.core.crawler.ICrawlerConfig;
import com.norconex.collector.http.crawler.AbstractHttpTest;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.committer.core.impl.NilCommitter;
import com.norconex.jef4.status.JobState;


/**
 * @author Pascal Essiembre
 */
public class HttpCollectorTest extends AbstractHttpTest {

    private static final Logger LOG = 
            LogManager.getLogger(HttpCollectorTest.class);
    
    @Test
    public void testRerunInJVM() throws IOException, InterruptedException {
         
        TemporaryFolder folder = getTempFolder();
        
        HttpCrawlerConfig crawlerCfg = new HttpCrawlerConfig();
        crawlerCfg.setId("test-crawler");
        crawlerCfg.setCommitter(new NilCommitter());
        String startURL = newUrl("/test?case=modifiedFiles");
        crawlerCfg.setStartURLs(startURL);
        GenericDelayResolver delay = new GenericDelayResolver();
        delay.setDefaultDelay(10);
        crawlerCfg.setDelayResolver(delay);
        crawlerCfg.setWorkDir(folder.newFolder("multiRunTest"));
        
        HttpCollectorConfig config = new HttpCollectorConfig();
        config.setId("test-http-collector");
        config.setLogsDir(crawlerCfg.getWorkDir().getAbsolutePath());
        config.setProgressDir(crawlerCfg.getWorkDir().getAbsolutePath());
        config.setCrawlerConfigs(new ICrawlerConfig[] {crawlerCfg});
        config.setCollectorListeners(new ICollectorLifeCycleListener() {
            @Override
            public void onCollectorStart(ICollector collector) {
            }
            @Override
            public void onCollectorFinish(ICollector collector) {
                JobState state = collector.getJobSuite().getStatus().getState();
                assertTrue("Invalid state: " + state, 
                        state == JobState.COMPLETED);
            }
        });
        
        final HttpCollector collector = new HttpCollector(config);
        
        collector.start(false);
        LOG.error("First normal run complete.");

        
        collector.start(false);
        LOG.error("Second normal run complete.");
    }
    
    
}
