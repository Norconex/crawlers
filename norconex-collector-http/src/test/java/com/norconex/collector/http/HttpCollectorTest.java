/* Copyright 2017-2019 Norconex Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.core.CollectorEvent;
import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.delay.impl.GenericDelayResolver;
import com.norconex.committer.core.impl.NilCommitter;
import com.norconex.jef5.status.JobState;


/**
 * @author Pascal Essiembre
 */
@Disabled
public class HttpCollectorTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCollectorTest.class);

    @TempDir
    Path folder;

    @Test
    public void testRerunInJVM() throws IOException, InterruptedException {

        HttpCrawlerConfig crawlerCfg = new HttpCrawlerConfig();
        crawlerCfg.setId("test-crawler");
        crawlerCfg.setCommitter(new NilCommitter());
        String startURL = null;//newUrl("/test?case=modifiedFiles");
        crawlerCfg.setStartURLs(startURL);
        GenericDelayResolver delay = new GenericDelayResolver();
        delay.setDefaultDelay(10);
        crawlerCfg.setDelayResolver(delay);

        HttpCollectorConfig config = new HttpCollectorConfig();
        config.setId("test-http-collector");
        config.setWorkDir(folder.resolve("multiRunTest"));
        config.setCrawlerConfigs(new CrawlerConfig[] {crawlerCfg});

        //TODO have abstract filter providing easy way to filter by event
        // type and event name.
        config.addEventListeners(e -> {
            if (e.is(HttpCollectorEvent.COLLECTOR_ENDED)) {
                JobState state = ((CollectorEvent<?>) e).getSource().getState();
                assertEquals(JobState.COMPLETED, state);
            }
        });

        final HttpCollector collector = new HttpCollector(config);

        collector.start(false);
        LOG.debug("First normal run complete.");


        collector.start(false);
        LOG.debug("Second normal run complete.");
    }
}
