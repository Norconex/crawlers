/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.junit.cluster.node;

import com.norconex.crawler.core.doc.operations.DocumentConsumer;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.junit.cluster.state.StateDbClient;
import com.norconex.importer.doc.Doc;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class ConfigInstrumentor {

    private ConfigInstrumentor() {
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PostImportWaitForEventOnNodes
            implements DocumentConsumer {
        private int numNodes = -1;
        private String eventName;

        @Override
        public void accept(Fetcher fetcher, Doc doc) {
            LOG.info("Waiting for {} nodes to have fired.", numNodes);
            StateDbClient.get()
                    .waitFor()
                    .numNodes(numNodes)
                    .toHaveFired(eventName);
        }
    }
}
