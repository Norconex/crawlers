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
import com.norconex.crawler.core.cluster.ClusterConnector;
import com.norconex.crawler.core.doc.operations.DocumentConsumer;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.junit.cluster.node.ConfigInstrumentor.PostImportWaitForEventOnNodes;
import com.norconex.crawler.core.mocks.cluster.MockFailingClusterConnector;
import com.norconex.crawler.core.mocks.fetch.MockFetcher;

public class CoreTestPtProvider extends BasePolymorphicTypeProvider {

    @Override
    protected void register(Registry registry) {
        registry
                .add(DocumentConsumer.class,
                        PostImportWaitForEventOnNodes.class)
                .add(Fetcher.class,
                        MockFetcher.class)
                //                .add(EventListener.class,
                //                        TestEventMemoryListener.class
                //                        ,
                //                        CrawlTestCapturer.class
                //                )
                //                .add(ImporterResponseProcessor.class,
                //                        ProcessUpsertTest.TestResponseProcessor.class)
                .add(ClusterConnector.class,
                        //                        TestClusterConnector.ClusterNoPersistence.class,
                        //                        TestClusterConnector.ClusterWithPersistence.class,
                        //                        TestClusterConnector.StandaloneNoPersistence.class,
                        //                        TestClusterConnector.StandaloneWithPersistence.class,
                        MockFailingClusterConnector.class
                //                        ,
                //                        MockSingleNodeConnector.class,
                //                        MockMultiNodesConnector.class
                );

    }
}
