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
package com.norconex.crawler.core.cluster.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.management.ManagementFactory;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.context.CrawlContext;

/**
 * Tests for {@link PipelineProgressJMX} register/unregister operations.
 */
class PipelineProgressJMXTest {

    // -----------------------------------------------------------------
    // register + unregister lifecycle
    // -----------------------------------------------------------------

    @Test
    void registerAndUnregister_noException() {
        var ctx = buildMockContext("pp-jmx-test-" + shortUUID());
        var manager = mock(PipelineManager.class);
        when(manager.getPipelineProgress("pipe-1"))
                .thenReturn(PipelineProgress.builder().build());

        assertThatNoException().isThrownBy(() -> {
            PipelineProgressJMX.register(ctx, manager, "pipe-1");
            PipelineProgressJMX.unregister(ctx);
        });
    }

    @Test
    void unregister_whenNotRegistered_noException() {
        var ctx = buildMockContext("pp-jmx-unreg-" + shortUUID());
        assertThatNoException().isThrownBy(
                () -> PipelineProgressJMX.unregister(ctx));
    }

    @Test
    void register_isVisibleInMBeanServer() throws Exception {
        var crawlerId = "pp-jmx-vis-" + shortUUID();
        var ctx = buildMockContext(crawlerId);
        var manager = mock(PipelineManager.class);

        try {
            PipelineProgressJMX.register(ctx, manager, "pipe-vis");
            var mbs = ManagementFactory.getPlatformMBeanServer();
            var names = mbs.queryNames(null, null);
            boolean found = names.stream().anyMatch(n -> n.toString().contains(
                    "crawler=" + javax.management.ObjectName.quote(crawlerId)));
            assertThat(found).isTrue();
        } finally {
            PipelineProgressJMX.unregister(ctx);
        }
    }

    @Test
    void doubleUnregister_noException() {
        var ctx = buildMockContext("pp-jmx-double-" + shortUUID());
        var manager = mock(PipelineManager.class);

        assertThatNoException().isThrownBy(() -> {
            PipelineProgressJMX.register(ctx, manager, "pipe-double");
            PipelineProgressJMX.unregister(ctx);
            PipelineProgressJMX.unregister(ctx);
        });
    }

    // -----------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------

    private static CrawlContext buildMockContext(String crawlerId) {
        var ctx = mock(CrawlContext.class);
        when(ctx.getId()).thenReturn(crawlerId);
        return ctx;
    }

    private static String shortUUID() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }
}
