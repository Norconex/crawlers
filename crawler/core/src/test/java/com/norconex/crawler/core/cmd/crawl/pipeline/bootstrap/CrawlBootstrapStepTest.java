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
package com.norconex.crawler.core.cmd.crawl.pipeline.bootstrap;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.session.CrawlSession;

/**
 * Tests for {@link CrawlBootstrapStep}.
 */
@Timeout(30)
class CrawlBootstrapStepTest {

    @Test
    void noBootstrappers_doesNothing() {
        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getBootstrappers())
                .thenReturn(Collections.emptyList());

        var step = new CrawlBootstrapStep("test-step");
        step.execute(session);

        // No bootstrapper was ever called — no interaction beyond setup
        verify(crawlContext).getBootstrappers();
    }

    @Test
    void withBootstrappers_eachIsCalled() {
        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        var b1 = mock(CrawlBootstrapper.class);
        var b2 = mock(CrawlBootstrapper.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getBootstrappers()).thenReturn(List.of(b1, b2));

        var step = new CrawlBootstrapStep("test-step");
        step.execute(session);

        verify(b1).bootstrap(session);
        verify(b2).bootstrap(session);
    }

    @Test
    void withOneBootstrapper_otherIsNotCalled() {
        var session = mock(CrawlSession.class);
        var crawlContext = mock(CrawlContext.class);
        var b1 = mock(CrawlBootstrapper.class);
        var b2 = mock(CrawlBootstrapper.class);
        when(session.getCrawlContext()).thenReturn(crawlContext);
        when(crawlContext.getBootstrappers()).thenReturn(List.of(b1));

        var step = new CrawlBootstrapStep("test-step");
        step.execute(session);

        verify(b1).bootstrap(session);
        verify(b2, never()).bootstrap(session);
    }
}
