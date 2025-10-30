/*
 * Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.infinispan;

import java.util.function.Consumer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.junit.CrawlTest;

/**
 * Example test demonstrating how to use FastLocalInfinispanClusterConnector
 * for faster cluster tests.
 *
 * <p><b>Performance Comparison:</b></p>
 * <ul>
 *   <li><b>Standard connector:</b> 20-30s cluster formation per test</li>
 *   <li><b>Fast connector:</b> 3-7s cluster formation per test</li>
 *   <li><b>Expected speedup:</b> 2-3x faster</li>
 * </ul>
 *
 * <p><b>Trade-offs:</b></p>
 * <ul>
 *   <li>✅ Faster test execution</li>
 *   <li>✅ Same persistence behavior</li>
 *   <li>⚠️ SHARED_LOOPBACK only works on single machine</li>
 *   <li>⚠️ Less realistic network simulation</li>
 * </ul>
 */
@Disabled("Example test - enable to measure performance improvement")
class FastClusterConnectorExample {

    /**
     * Example test using the STANDARD InfinispanClusterConnector.
     * Expected: ~25-35s cluster formation time.
     */
    @Test
    @CrawlTest(
        // Uses default clusters (standard InfinispanClusterConnector)
        clusters = InfinispanClusterConnector.class
    )
    void testWithStandardConnector() {
        // Default behavior - uses InfinispanClusterConnector
        // Cluster formation: 20-30s (TCP + MPING discovery)
    }

    /**
     * Example test using the FAST connector for local testing.
     * Expected: ~5-10s cluster formation time.
     */
    @Test
    @CrawlTest(
        // Use fast connector directly via clusters parameter
        clusters = FastLocalInfinispanClusterConnector.class
    )
    void testWithFastConnector() {
        // Fast behavior - uses FastLocalInfinispanClusterConnector
        // Cluster formation: 3-7s (SHARED_LOOPBACK in-memory transport)
    }

    /**
     * Example showing how to conditionally use fast connector via
     * configModifier.
     */
    @Test
    @CrawlTest(
        clusters = InfinispanClusterConnector.class,
        configModifier = FastConnectorModifier.class
    )
    void testWithConditionalFastConnector() {
        // Run with: FAST_TESTS=true mvn test -pl crawler/core -Dtest=FastClusterConnectorExample#testWithConditionalFastConnector
        // Without FAST_TESTS: uses standard connector (20-30s)
        // With FAST_TESTS=true: uses fast connector (3-7s)
    }

    /**
     * Config modifier that conditionally switches to fast connector
     * based on FAST_TESTS environment variable.
     */
    public static class FastConnectorModifier implements Consumer<CrawlConfig> {
        @Override
        public void accept(CrawlConfig config) {
            String fastTests = System.getenv("FAST_TESTS");
            if ("true".equalsIgnoreCase(fastTests)) {
                config.setClusterConnector(
                        new FastLocalInfinispanClusterConnector());
            }
        }
    }
}
