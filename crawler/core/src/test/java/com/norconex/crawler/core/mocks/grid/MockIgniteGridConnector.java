/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.mocks.grid;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.impl.ignite.IgniteGridConnector;

import lombok.Getter;
import lombok.Setter;

/**
 * Runs embedded ignite servers.
 */
public class MockIgniteGridConnector extends IgniteGridConnector {

    //    @Getter
    //    private final GridConnectorConfig configuration =
    //            new MockIgniteGridConnectorConfig();

    @Getter
    @Setter
    private int serverNodes = 1;

    @Override
    public Grid connect(CrawlerContext crawlerContext) {
        return new IgniteGridConnector(new MockIgniteGridInstanceClient(
                crawlerContext.getConfiguration().getWorkDir(),
                serverNodes)).connect(crawlerContext);
    }

    //    @Data
    //    public class MockIgniteGridConnectorConfig
    //            extends IgniteGridConnectorConfig {
    //        private int serverNodes = 1;
    //    }

}
