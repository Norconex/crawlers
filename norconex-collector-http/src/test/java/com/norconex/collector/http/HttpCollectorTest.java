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

import static org.junit.Assert.assertEquals;

import java.io.IOException;

import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorEvent;
import com.norconex.collector.core.crawler.ICrawlerConfig;
import com.norconex.collector.http.crawler.AbstractHttpTest;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.committer.core.impl.NilCommitter;
import com.norconex.jef5.status.JobState;


/**
 * @author Pascal Essiembre
 */
public class HttpCollectorTest extends AbstractHttpTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCollectorTest.class);

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
        crawlerCfg.setWorkDir(folder.newFolder("multiRunTest").toPath());

        HttpCollectorConfig config = new HttpCollectorConfig();
        config.setId("test-http-collector");
//        config.setLogsDir(crawlerCfg.getWorkDir().toAbsolutePath());
//        config.setProgressDir(crawlerCfg.getWorkDir().toAbsolutePath());
        config.setCrawlerConfigs(new ICrawlerConfig[] {crawlerCfg});

        //TODO have abstract filter providing easy way to filter by event
        // type and event name.
        config.addEventListeners(e -> {
            if (e.is(HttpCollectorEvent.COLLECTOR_ENDED)) {
                JobState state = ((CollectorEvent<?>) e).getSource()
                        .getJobSuite().getRootStatus().getState();
                assertEquals(JobState.COMPLETED, state);
            }

//            JobState state = collector.getJobSuite().getStatus().getState();

        });

//        config.setCollectorListeners(new ICollectorLifeCycleListener() {
//            @Override
//            public void onCollectorStart(ICollector collector) {
//            }
//            @Override
//            public void onCollectorFinish(ICollector collector) {
//                JobState state = collector.getJobSuite().getStatus().getState();
//                assertTrue("Invalid state: " + state,
//                        state == JobState.COMPLETED);
//            }
//        });

        final HttpCollector collector = new HttpCollector(config);

        collector.start(false);
        LOG.debug("First normal run complete.");


        collector.start(false);
        LOG.debug("Second normal run complete.");
    }


}
