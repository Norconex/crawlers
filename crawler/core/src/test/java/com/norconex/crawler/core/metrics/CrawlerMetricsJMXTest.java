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
package com.norconex.crawler.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.management.ManagementFactory;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.context.CrawlContext;

/**
 * Tests for {@link CrawlerMetricsJMX} register/unregister operations.
 */
class CrawlerMetricsJMXTest {

    // -----------------------------------------------------------------
    // register (null check)
    // -----------------------------------------------------------------

    @Test
    void register_nullContext_throwsNullPointerException() {
        assertThatThrownBy(() -> CrawlerMetricsJMX.register(null))
                .isInstanceOf(NullPointerException.class);
    }

    // -----------------------------------------------------------------
    // unregister (null check)
    // -----------------------------------------------------------------

    @Test
    void unregister_nullContext_throwsNullPointerException() {
        assertThatThrownBy(() -> CrawlerMetricsJMX.unregister(null))
                .isInstanceOf(NullPointerException.class);
    }

    // -----------------------------------------------------------------
    // register + unregister lifecycle
    // -----------------------------------------------------------------

    @Test
    void registerAndUnregister_noException() {
        var ctx = buildMockContext("jmx-metrics-test-" + shortUUID());
        assertThatNoException().isThrownBy(() -> {
            CrawlerMetricsJMX.register(ctx);
            CrawlerMetricsJMX.unregister(ctx);
        });
    }

    @Test
    void unregister_whenNotRegistered_noException() {
        // Should silently do nothing when the bean was never registered
        var ctx = buildMockContext("jmx-metrics-unreg-" + shortUUID());
        assertThatNoException().isThrownBy(
                () -> CrawlerMetricsJMX.unregister(ctx));
    }

    @Test
    void register_isVisibleInMBeanServer() throws Exception {
        var crawlerId = "jmx-metrics-vis-" + shortUUID();
        var ctx = buildMockContext(crawlerId);
        try {
            CrawlerMetricsJMX.register(ctx);
            var mbs = ManagementFactory.getPlatformMBeanServer();
            // Verify at least one MBean with our crawler name is registered
            var names = mbs.queryNames(null, null);
            boolean found = names.stream()
                    .anyMatch(n -> n.toString().contains(
                            "crawler=" + javax.management.ObjectName.quote(
                                    crawlerId)));
            assertThat(found).isTrue();
        } finally {
            CrawlerMetricsJMX.unregister(ctx);
        }
    }

    @Test
    void doubleUnregister_noException() {
        var ctx = buildMockContext("jmx-metrics-double-" + shortUUID());
        assertThatNoException().isThrownBy(() -> {
            CrawlerMetricsJMX.register(ctx);
            CrawlerMetricsJMX.unregister(ctx);
            // Second unregister is a no-op since isRegistered check prevents it
            CrawlerMetricsJMX.unregister(ctx);
        });
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static CrawlContext buildMockContext(String crawlerId) {
        var ctx = mock(CrawlContext.class);
        when(ctx.getId()).thenReturn(crawlerId);
        when(ctx.getMetrics()).thenReturn(new CrawlerMetricsImpl());
        return ctx;
    }

    private static String shortUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
