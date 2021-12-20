/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web.feature;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import com.norconex.collector.core.crawler.CrawlerConfig;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.TestUtil;
import com.norconex.collector.http.crawler.HttpCrawlerConfig;
import com.norconex.collector.http.web.AbstractInfiniteDepthTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Test that MaxDepth setting is respected.
 * @author Pascal Essiembre
 */
public class MaxConcurrentCrawlers extends AbstractInfiniteDepthTestFeature {

    // left=maxConcurrentCrawlers, right=number of crawlers
    private final List<Pair<Integer, Integer>> perRunTestValues =
            new ArrayList<>();

    public MaxConcurrentCrawlers() {
        perRunTestValues.add(new MutablePair<>(-1, 1));
        perRunTestValues.add(new MutablePair<>(-1, 3));
        perRunTestValues.add(new MutablePair<>(1, 3));
        perRunTestValues.add(new MutablePair<>(2, 3));
        perRunTestValues.add(new MutablePair<>(3, 3));
        perRunTestValues.add(new MutablePair<>(1, 1));
        perRunTestValues.add(new MutablePair<>(3, 1));
    }

    @Override
    public int numberOfRun() {
        return perRunTestValues.size();
    }

    @Override
    protected void doConfigureCollector(HttpCollectorConfig collectorConfig)
            throws Exception {
        Pair<Integer, Integer> testValues = perRunTestValues.get(getRunIndex());

        collectorConfig.setMaxConcurrentCrawlers(testValues.getLeft());

        List<String> startURLs =
                TestUtil.firstCrawlerConfig(collectorConfig).getStartURLs();

        List<CrawlerConfig> crawlerConfigs = new ArrayList<>();
        for (int i = 0; i < testValues.getRight(); i++) {
            crawlerConfigs.add(createCrawlerConfig(
                    "max" + testValues.getLeft()
                  + "_num" + (i + 1)
                  + "of" + testValues.getRight()
                  + "_run" + getRunCount(),
                    startURLs));
        }
        collectorConfig.setCrawlerConfigs(crawlerConfigs);
    }

    @Override
    protected void doConfigureCralwer(HttpCrawlerConfig cfg)
            throws Exception {
        cfg.setMaxDocuments(5);
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        // they should all have 5 docs
        assertListSize("document", committer.getUpsertRequests(), 5);
    }


    private HttpCrawlerConfig createCrawlerConfig(
            String crawlerId, List<String> startURLs) {
        return TestUtil.newMemoryCrawlerConfig(
                crawlerId, startURLs.toArray(ArrayUtils.EMPTY_STRING_ARRAY));
    }
}
