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
package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

class StringJdbcMapStoreTest {

    //    @TempDir
    //    private Path tempDir;
    //
    //    private HazelcastInstance hazelcast;
    //    private IMap<String, String> map;
    //
    //    @BeforeEach
    //    void setUp() {
    //        var config = HazelcastConfigLoader.load(
    //                HazelcastClusterConnectorConfig.DEFAULT_CONFIG_FILE, tempDir);
    //        hazelcast = Hazelcast.newHazelcastInstance(config);
    //        map = hazelcast.getMap("testMap");
    //    }
    //
    //    @AfterEach
    //    void tearDown() {
    //        if (hazelcast != null) {
    //            hazelcast.shutdown();
    //        }
    //    }
    //
    //    @Test
    //    void testStoreAndLoad() {
    //        map.put("foo", "bar");
    //        var value = map.get("foo");
    //        assertThat(value).isEqualTo("bar");
    //    }
    //
    //    @Test
    //    void testStoreAllAndLoadAll() {
    //        map.putAll(Map.of("a", "1", "b", "2", "c", "3"));
    //        var loaded = map.getAll(Set.of("a", "b", "c"));
    //        assertThat(loaded).containsExactlyInAnyOrderEntriesOf(
    //                Map.of("a", "1", "b", "2", "c", "3"));
    //    }
    //
    //    @Test
    //    void testDeleteAndDeleteAll() {
    //        map.putAll(Map.of("x", "y", "y", "z"));
    //        map.delete("x");
    //        assertThat(map.get("x")).isNull();
    //        map.delete("y");
    //        assertThat(map.get("y")).isNull();
    //    }
    //
    //    @Test
    //    void testLoadAllKeys() {
    //        map.putAll(Map.of("k1", "v1", "k2", "v2"));
    //        List<String> keys = List.copyOf(map.keySet());
    //        assertThat(keys).containsExactlyInAnyOrder("k1", "k2");
    //    }
}
