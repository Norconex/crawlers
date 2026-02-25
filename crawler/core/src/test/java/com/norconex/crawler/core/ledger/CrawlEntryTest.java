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
package com.norconex.crawler.core.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class CrawlEntryTest {

    // -----------------------------------------------------------------
    // withReference
    // -----------------------------------------------------------------

    @Test
    void withReference_createsClone_withNewReference() {
        var original = new CrawlEntry("http://original.com");
        original.setDepth(3);
        original.setProcessingStatus(ProcessingStatus.QUEUED);

        var clone = original.withReference("http://clone.com");

        assertThat(clone.getReference()).isEqualTo("http://clone.com");
        assertThat(clone.getDepth()).isEqualTo(3);
        assertThat(clone.getProcessingStatus())
                .isEqualTo(ProcessingStatus.QUEUED);
        // original should be unchanged
        assertThat(original.getReference()).isEqualTo("http://original.com");
    }

    // -----------------------------------------------------------------
    // referenceTrail
    // -----------------------------------------------------------------

    @Test
    void getReferenceTrail_returnsUnmodifiableList() {
        var entry = new CrawlEntry("http://test.com");
        entry.addToReferenceTrail("http://hop1.com");
        entry.addToReferenceTrail("http://hop2.com");

        var trail = entry.getReferenceTrail();

        assertThat(trail).containsExactly("http://hop1.com", "http://hop2.com");
        assertThatThrownBy(() -> trail.add("http://illegal.com"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void addToReferenceTrail_appendsInOrder() {
        var entry = new CrawlEntry("ref://main");
        entry.addToReferenceTrail("ref://first");
        entry.addToReferenceTrail("ref://second");
        entry.addToReferenceTrail("ref://third");

        assertThat(entry.getReferenceTrail())
                .containsExactly("ref://first", "ref://second", "ref://third");
    }

    @Test
    void setReferenceTrail_replacesExistingTrail() {
        var entry = new CrawlEntry("ref://main");
        entry.addToReferenceTrail("ref://old");

        entry.setReferenceTrail(List.of("ref://new1", "ref://new2"));

        assertThat(entry.getReferenceTrail())
                .containsExactly("ref://new1", "ref://new2");
    }

    @Test
    void setReferenceTrail_withNull_clearsTrail() {
        var entry = new CrawlEntry("ref://main");
        entry.addToReferenceTrail("ref://existing");

        entry.setReferenceTrail(null);

        assertThat(entry.getReferenceTrail()).isEmpty();
    }

    @Test
    void setReferenceTrail_withEmptyList_clearsTrail() {
        var entry = new CrawlEntry("ref://main");
        entry.addToReferenceTrail("ref://existing");

        entry.setReferenceTrail(new ArrayList<>());

        assertThat(entry.getReferenceTrail()).isEmpty();
    }

    // -----------------------------------------------------------------
    // metaChecksum
    // -----------------------------------------------------------------

    @Test
    void setAndGetMetaChecksum() {
        var entry = new CrawlEntry("ref://checksum");
        assertThat(entry.getMetaChecksum()).isNull();

        entry.setMetaChecksum("abc123");
        assertThat(entry.getMetaChecksum()).isEqualTo("abc123");

        entry.setMetaChecksum(null);
        assertThat(entry.getMetaChecksum()).isNull();
    }

    // -----------------------------------------------------------------
    // contentChecksum
    // -----------------------------------------------------------------

    @Test
    void setAndGetContentChecksum() {
        var entry = new CrawlEntry("ref://content");
        assertThat(entry.getContentChecksum()).isNull();

        entry.setContentChecksum("sha256-xyz");
        assertThat(entry.getContentChecksum()).isEqualTo("sha256-xyz");

        entry.setContentChecksum(null);
        assertThat(entry.getContentChecksum()).isNull();
    }

    // -----------------------------------------------------------------
    // queuedAt
    // -----------------------------------------------------------------

    @Test
    void setAndGetQueuedAt() {
        var entry = new CrawlEntry("ref://time");
        assertThat(entry.getQueuedAt()).isNull();

        var now = ZonedDateTime.now();
        entry.setQueuedAt(now);
        assertThat(entry.getQueuedAt()).isEqualTo(now);

        entry.setQueuedAt(null);
        assertThat(entry.getQueuedAt()).isNull();
    }

    // -----------------------------------------------------------------
    // default constructor
    // -----------------------------------------------------------------

    @Test
    void defaultConstructor_createsEntryWithNullReference() {
        var entry = new CrawlEntry();
        assertThat(entry.getReference()).isNull();
        assertThat(entry.getDepth()).isZero();
        assertThat(entry.getProcessingStatus())
                .isEqualTo(ProcessingStatus.UNTRACKED);
    }
}
