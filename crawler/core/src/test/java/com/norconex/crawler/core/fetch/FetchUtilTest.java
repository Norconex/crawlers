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
package com.norconex.crawler.core.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core.context.CrawlContext;
import com.norconex.crawler.core.ledger.ProcessingOutcome;

/**
 * Unit tests for {@link FetchUtil}.
 *
 * Tests cover all branches of {@link FetchUtil#shouldContinueOnBadStatus}.
 */
@Timeout(30)
class FetchUtilTest {

    private CrawlContext crawler;
    private CrawlConfig config;

    @BeforeEach
    void setUp() {
        crawler = mock(CrawlContext.class);
        config = new CrawlConfig();
        when(crawler.getCrawlConfig()).thenReturn(config);
    }

    // -----------------------------------------------------------------
    // METADATA directive (HEAD-like)
    // -----------------------------------------------------------------

    @Test
    void testMetadata_required_stopOnBadStatus() {
        config.setMetadataFetchSupport(FetchDirectiveSupport.REQUIRED);
        assertThat(FetchUtil.shouldContinueOnBadStatus(
                crawler, null, FetchDirective.METADATA)).isFalse();
    }

    @Test
    void testMetadata_optional_withDocumentEnabled_shouldContinue() {
        config.setMetadataFetchSupport(FetchDirectiveSupport.OPTIONAL);
        config.setDocumentFetchSupport(FetchDirectiveSupport.REQUIRED);
        assertThat(FetchUtil.shouldContinueOnBadStatus(
                crawler, null, FetchDirective.METADATA)).isTrue();
    }

    @Test
    void testMetadata_optional_withDocumentDisabled_shouldNotContinue() {
        config.setMetadataFetchSupport(FetchDirectiveSupport.OPTIONAL);
        config.setDocumentFetchSupport(FetchDirectiveSupport.DISABLED);
        assertThat(FetchUtil.shouldContinueOnBadStatus(
                crawler, null, FetchDirective.METADATA)).isFalse();
    }

    // -----------------------------------------------------------------
    // DOCUMENT directive (GET-like)
    // -----------------------------------------------------------------

    @Test
    void testDocument_required_stopOnBadStatus() {
        config.setDocumentFetchSupport(FetchDirectiveSupport.REQUIRED);
        assertThat(FetchUtil.shouldContinueOnBadStatus(
                crawler, ProcessingOutcome.NEW, FetchDirective.DOCUMENT))
                        .isFalse();
    }

    @Test
    void testDocument_optional_metadataEnabled_priorSuccess_shouldContinue() {
        config.setMetadataFetchSupport(FetchDirectiveSupport.OPTIONAL);
        config.setDocumentFetchSupport(FetchDirectiveSupport.OPTIONAL);
        assertThat(FetchUtil.shouldContinueOnBadStatus(
                crawler, ProcessingOutcome.NEW, FetchDirective.DOCUMENT))
                        .isTrue();
    }

    @Test
    void testDocument_optional_metadataDisabled_shouldNotContinue() {
        config.setMetadataFetchSupport(FetchDirectiveSupport.DISABLED);
        config.setDocumentFetchSupport(FetchDirectiveSupport.OPTIONAL);
        assertThat(FetchUtil.shouldContinueOnBadStatus(
                crawler, ProcessingOutcome.NEW, FetchDirective.DOCUMENT))
                        .isFalse();
    }

    @Test
    void testDocument_optional_metadataEnabled_priorError_shouldNotContinue() {
        config.setMetadataFetchSupport(FetchDirectiveSupport.OPTIONAL);
        config.setDocumentFetchSupport(FetchDirectiveSupport.OPTIONAL);
        assertThat(FetchUtil.shouldContinueOnBadStatus(
                crawler, ProcessingOutcome.ERROR, FetchDirective.DOCUMENT))
                        .isFalse();
    }

    // -----------------------------------------------------------------
    // FetchDirectiveSupport helpers
    // -----------------------------------------------------------------

    @Test
    void testFetchDirectiveSupport_is() {
        assertThat(FetchDirectiveSupport.DISABLED
                .is(FetchDirectiveSupport.DISABLED)).isTrue();
        assertThat(FetchDirectiveSupport.DISABLED.is(null)).isTrue();
        assertThat(FetchDirectiveSupport.REQUIRED
                .is(FetchDirectiveSupport.REQUIRED)).isTrue();
        assertThat(FetchDirectiveSupport.OPTIONAL
                .is(FetchDirectiveSupport.REQUIRED)).isFalse();
    }

    @Test
    void testFetchDirectiveSupport_isEnabled() {
        assertThat(FetchDirectiveSupport.isEnabled(
                FetchDirectiveSupport.OPTIONAL)).isTrue();
        assertThat(FetchDirectiveSupport.isEnabled(
                FetchDirectiveSupport.REQUIRED)).isTrue();
        assertThat(FetchDirectiveSupport.isEnabled(
                FetchDirectiveSupport.DISABLED)).isFalse();
        assertThat(FetchDirectiveSupport.isEnabled(null)).isFalse();
    }
}
