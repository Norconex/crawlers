/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.doc.operations.spoil.impl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.doc.operations.spoil.SpoiledReferenceStrategy;
import com.norconex.crawler.core.ledger.ProcessingOutcome;

@Timeout(30)
class GenericSpoiledReferenceStrategizerTest {

    @Test
    void notFound_mapsToDelete() {
        var strategizer = new GenericSpoiledReferenceStrategizer();
        assertThat(strategizer.resolveSpoiledReferenceStrategy(
                "http://example.com/gone", ProcessingOutcome.NOT_FOUND))
                        .isEqualTo(SpoiledReferenceStrategy.DELETE);
    }

    @Test
    void badStatus_mapsToGraceOnce() {
        var strategizer = new GenericSpoiledReferenceStrategizer();
        assertThat(strategizer.resolveSpoiledReferenceStrategy(
                "http://example.com/broken", ProcessingOutcome.BAD_STATUS))
                        .isEqualTo(SpoiledReferenceStrategy.GRACE_ONCE);
    }

    @Test
    void error_mapsToGraceOnce() {
        var strategizer = new GenericSpoiledReferenceStrategizer();
        assertThat(strategizer.resolveSpoiledReferenceStrategy(
                "http://example.com/error", ProcessingOutcome.ERROR))
                        .isEqualTo(SpoiledReferenceStrategy.GRACE_ONCE);
    }

    @Test
    void unmappedState_usesDefaultFallback() {
        var strategizer = new GenericSpoiledReferenceStrategizer();
        // REJECTED has no default mapping → falls back to DELETE
        assertThat(strategizer.resolveSpoiledReferenceStrategy(
                "http://example.com/rejected", ProcessingOutcome.REJECTED))
                        .isEqualTo(
                                GenericSpoiledReferenceStrategizerConfig.DEFAULT_FALLBACK_STRATEGY);
    }

    @Test
    void customFallbackStrategy_usedForUnmappedStates() {
        var strategizer = new GenericSpoiledReferenceStrategizer();
        strategizer.getConfiguration()
                .setFallbackStrategy(SpoiledReferenceStrategy.IGNORE);

        assertThat(strategizer.resolveSpoiledReferenceStrategy(
                "http://example.com/unknown", ProcessingOutcome.REJECTED))
                        .isEqualTo(SpoiledReferenceStrategy.IGNORE);
    }

    @Test
    void customMapping_overridesDefault() {
        var strategizer = new GenericSpoiledReferenceStrategizer();
        strategizer.getConfiguration()
                .setMapping(ProcessingOutcome.NOT_FOUND,
                        SpoiledReferenceStrategy.GRACE_ONCE);

        assertThat(strategizer.resolveSpoiledReferenceStrategy(
                "http://example.com/gone", ProcessingOutcome.NOT_FOUND))
                        .isEqualTo(SpoiledReferenceStrategy.GRACE_ONCE);
    }

    @Test
    void nullFallbackStrategy_usesClassDefault() {
        var strategizer = new GenericSpoiledReferenceStrategizer();
        strategizer.getConfiguration().setFallbackStrategy(null);

        // No mapping for REJECTED, null fallback → class-level default (DELETE)
        assertThat(strategizer.resolveSpoiledReferenceStrategy(
                "http://example.com/any", ProcessingOutcome.REJECTED))
                        .isEqualTo(
                                GenericSpoiledReferenceStrategizerConfig.DEFAULT_FALLBACK_STRATEGY);
    }
}
