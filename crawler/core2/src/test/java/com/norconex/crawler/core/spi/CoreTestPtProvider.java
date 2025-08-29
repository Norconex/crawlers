/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.crawler.core.spi;

import com.norconex.commons.lang.bean.spi.BasePolymorphicTypeProvider;
import com.norconex.commons.lang.event.EventListener;
import com.norconex.crawler.core2.cluster.ClusterConnector;
import com.norconex.crawler.core2.cmd.crawl.pipeline.process.ProcessUpsertTest;
import com.norconex.crawler.core2.junit.CrawlTestCapturer;
import com.norconex.crawler.core2.mocks.cli.MockCliEventWriter;
import com.norconex.crawler.core2.mocks.cluster.MockFailingClusterConnector;
import com.norconex.crawler.core2.mocks.cluster.MockMultiNodesConnector;
import com.norconex.crawler.core2.mocks.cluster.MockSingleNodeConnector;
import com.norconex.importer.response.ImporterResponseProcessor;

public class CoreTestPtProvider extends BasePolymorphicTypeProvider {

    @Override
    protected void register(Registry registry) {
        registry
                .add(EventListener.class, MockCliEventWriter.class,
                        CrawlTestCapturer.class)
                .add(ImporterResponseProcessor.class,
                        ProcessUpsertTest.TestResponseProcessor.class)
                .add(ClusterConnector.class,
                        MockFailingClusterConnector.class,
                        MockSingleNodeConnector.class,
                        MockMultiNodesConnector.class);

    }
}
