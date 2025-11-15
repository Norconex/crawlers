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
package com.norconex.crawler.core.cluster;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.cluster.admin.ClusterAdminClient;
import com.norconex.crawler.core.junit.cluster.CrawlerCluster;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;

public class ClusterTest {

    @Test
    @Timeout(120)
    void testCluster(@TempDir Path tempDir)
            throws InterruptedException, IOException {
        var cfg = new CrawlConfig()
                .setId("" + TimeIdGenerator.next())
                .setWorkDir(tempDir);
        var starter = CrawlerNode.builder()
                .appArg("start")
                .exportEvents(true)
                .build();
        try (var cluster = new CrawlerCluster(cfg)) {
            // Launch 2 crawler nodes
            cluster.launch(starter, 2);

            //TODO QUERY CLUSTER STATE HERE

            cluster.waitForClusterFormation(2, Duration.ofSeconds(15));
            var size = ClusterAdminClient.builder()
                    .crawlerId(cfg.getId())
                    .build()
                    .clusterSize();

            System.err.println("SIZE is: " + size);

            var results =
                    cluster.waitForClusterTermination(Duration.ofSeconds(5));
            System.out.println("\nXXX NODE STDOUT:\n\n" + results.getStdOut());
            System.out.println("\nXXX NODE STDERR:\n\n" + results.getStdErr());

        }

    }
}