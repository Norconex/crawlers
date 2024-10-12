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
package com.norconex.crawler.core.grid.impl.ignite;

import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridStorage;

import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@EqualsAndHashCode
@ToString
@RequiredArgsConstructor
public class IgniteGrid implements Grid {

    @NonNull
    private final IgniteInstance igniteInstance;

    @Override
    public GridStorage storage() {
        return new IgniteGridStorage(igniteInstance);
    }

    @Override
    public GridCompute compute() {
        return new IgniteGridCompute(igniteInstance);
    }

    @Override
    public void close() {
        // NOOP: Nothing to close
        //        igniteInstance.close();
    }

    //    @Getter
    //    private final IgniteGridConnectorConfig configuration =
    //            new IgniteGridConnectorConfig();
    //
    //    @Override
    //    public synchronized GridClient client(CrawlerClient crawlerClient) {
    //        if (client == null) {
    //            client = new IgniteGridClient(crawlerClient);
    //        }
    //        return client;
    //    }
    //
    //    @Override
    //    public GridServer server(Crawler crawler) {
    //        return new IgniteGridServer();
    //    }
    //
    //    @Override
    //    public void close() {
    //        if (client != null) {
    //            client.close();
    //            client = null;
    //        }
    //    }

}
