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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.io.File;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import com.hazelcast.config.ClasspathYamlConfig;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.IMap;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class JdbcPersistTest {

    private HazelcastInstance hz;
    private static final String CONFIG_PATH =
            "cache/hazelcast-standalone.yaml";
    private static final String DB_PATH = "./data/crawler_db.mv.db";

    @BeforeAll
    void setup() throws Exception {
        // Clean up previous DB file for a fresh test
        var dbFile = new File(DB_PATH);
        if (dbFile.exists()) {
            dbFile.delete();
        }

        //      LiquibaseMigrationRunner.runH2Migrations(dbFile.getAbsolutePath());

        var config = new ClasspathYamlConfig(CONFIG_PATH);
        // Force multicast disabled in code, regardless of YAML
        config.getNetworkConfig().getJoin().getMulticastConfig()
                .setEnabled(false);
        System.out.println("Multicast enabled (forced): " +
                config.getNetworkConfig().getJoin().getMulticastConfig()
                        .isEnabled());
        hz = Hazelcast.newHazelcastInstance(config);

    }

    @AfterAll
    void teardown() {
        if (hz != null)
            hz.shutdown();
    }

    @Test
    void testJdbcPersist() {
        IMap<String, String> map = hz.getMap("CRAWLER");

        map.put("mykey", "myvalue");

        // Use records directly
        var value = map.get("mykey");
        System.out.println("value: " + value);

        //        // Records are immutable, so create new instances for updates
        //        ProductRecord updated = new ProductRecord(
        //                product.id(),
        //                product.name(),
        //                new BigDecimal("899.99"),
        //                product.stock(),
        //                product.category());
        //        map.put(1L, updated);
    }

}
