/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.cases.recovery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.norconex.grid.local.LocalGridConnector;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.junit.WebCrawlTest;

import lombok.extern.slf4j.Slf4j;

/**
 * Test that unhandled exceptions will abort the crawl as opposed,
 * for example, to have the process hanging and never returning.
 */
@Slf4j
class UnhandledExceptionJvmCrashTest {

    //TODO also test with IgniteGrid
    @WebCrawlTest(gridConnectors = LocalGridConnector.class)
    void testUnhandledExceptiotnJvmCrash(WebCrawlerConfig cfg) {
        // will throw at queuing:
        cfg.setStartReferences(List.of("\\Iam/Not/a/valid.url"));

        var outcome = ExternalCrawlSessionLauncher.start(cfg);
        LOG.debug(outcome.getStdErr());
        LOG.debug(outcome.getStdOut());
        assertThat(outcome.getReturnValue()).isEqualTo(1);
        assertThat(outcome.getStdOut()).contains("MalformedURLException");
        assertThat(outcome.getCommitterAfterLaunch().getUpsertCount()).isZero();
    }
}
