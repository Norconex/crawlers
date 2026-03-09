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
package com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap.queue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.ledger.CrawlEntry;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Tests for {@link QueueBootstrapper}.
 */
@Timeout(30)
class QueueBootstrapperTest {

    private CrawlSession buildSession(CrawlConfig config) {
        var crawlContext = mock(CrawlContext.class);
        when(crawlContext.getCrawlConfig()).thenReturn(config);
        when(crawlContext.createCrawlEntry(anyString()))
                .thenAnswer(inv -> new CrawlEntry(inv.getArgument(0)));

        var session = mock(CrawlSession.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(session.isResumed()).thenReturn(false);
        return session;
    }

    @Test
    void defaultConstructor_createsBootstrapper() {
        // Default constructor builds three enqueuers internally
        assertThat(new QueueBootstrapper()).isNotNull();
    }

    @Test
    void customConstructor_withEmptyList_createsBootstrapper() {
        assertThat(new QueueBootstrapper(List.of())).isNotNull();
    }

    @Test
    void customConstructor_withNullList_createsBootstrapper() {
        // Null list is treated as empty (no enqueuers)
        assertThat(new QueueBootstrapper(null)).isNotNull();
    }

    @Test
    void bootstrap_sync_withNoStartRefs_completesSuccessfully() {
        // Default CrawlConfig has no start references and async=false (sync)
        var config = new CrawlConfig();
        var session = buildSession(config);

        new QueueBootstrapper().bootstrap(session);

        // After sync bootstrap, queuing should be marked complete
        verify(session).setStartRefsQueueingComplete(true);
    }

    @Test
    void bootstrap_sync_whenResumed_logsResumeAndCompletes() {
        var config = new CrawlConfig();
        var session = buildSession(config);
        when(session.isResumed()).thenReturn(true);

        new QueueBootstrapper().bootstrap(session);

        verify(session).setStartRefsQueueingComplete(true);
    }

    @Test
    void bootstrap_withCustomEnqueuer_invokesEnqueuer() {
        // A custom enqueuer that records its invocations
        var invocations = new java.util.concurrent.atomic.AtomicInteger(0);
        ReferenceEnqueuer enqueuer = ctx -> {
            invocations.incrementAndGet();
            return 0;
        };

        var config = new CrawlConfig();
        var session = buildSession(config);

        new QueueBootstrapper(List.of(enqueuer)).bootstrap(session);

        assertThat(invocations.get()).isEqualTo(1);
    }
}
