/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.api.feature.crawl.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemDataStoreTest {

    @Test
    void testMemDataStore() {
        try (var engine = new MemDataStoreEngine(3)) {
            var store = engine.openStore("potato", String.class);

            // storing 4 but there should be 3 only
            store.save("k1", "v1");
            store.save("k2", "v2");
            store.save("k3", "v3");
            store.save("k4", "v4");

            assertThat(store.getName()).isEqualTo("potato");
            assertThat(store.count()).isEqualTo(3);
            assertThat(store.findFirst().get()).isEqualTo("v2");
            assertThat(store.forEach((k, v) -> !"k1".equals(k))).isTrue();
        }
    }

}
