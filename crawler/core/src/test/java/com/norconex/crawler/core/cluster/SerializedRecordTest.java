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
package com.norconex.crawler.core.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.ledger.CrawlEntry;

@Timeout(30)
class SerializedRecordTest {

    @Test
    void wrap_nonNull_populatesFields() {
        var entry = new CrawlEntry("http://example.com");
        var record = SerializedRecord.wrap(entry);

        assertThat(record.getClassName()).isEqualTo(CrawlEntry.class.getName());
        assertThat(record.getSerialized()).isNotBlank();
    }

    @Test
    void wrap_null_producesEmptyRecord() {
        var record = SerializedRecord.wrap(null);

        assertThat(record.getClassName()).isNull();
        assertThat(record.getSerialized()).isNull();
    }

    @Test
    void unwrap_nonNull_reconstructsObject() {
        var original = new CrawlEntry("http://test.com");
        original.setDepth(3);
        var record = SerializedRecord.wrap(original);

        CrawlEntry restored = record.unwrap();

        assertThat(restored).isNotNull();
        assertThat(restored.getReference()).isEqualTo("http://test.com");
        assertThat(restored.getDepth()).isEqualTo(3);
    }

    @Test
    void unwrap_nullSerialized_returnsNull() {
        var record = new SerializedRecord();
        record.setClassName(CrawlEntry.class.getName());
        // serialized is null → should return null

        assertThat(record.<CrawlEntry>unwrap()).isNull();
    }

    @Test
    void unwrap_emptySerialized_returnsNull() {
        var record = new SerializedRecord();
        record.setClassName(CrawlEntry.class.getName());
        record.setSerialized("");

        assertThat(record.<CrawlEntry>unwrap()).isNull();
    }

    @Test
    void unwrap_unknownClass_throwsCrawlerException() {
        var record = new SerializedRecord();
        record.setClassName("com.example.NonExistent");
        record.setSerialized("{\"reference\":\"test\"}");

        assertThatThrownBy(record::unwrap)
                .isInstanceOf(CrawlerException.class)
                .hasMessageContaining("Class not found");
    }

    @Test
    void unwrap_nullClassName_throwsCrawlerException() {
        var record = new SerializedRecord();
        record.setSerialized("{\"reference\":\"test\"}");

        assertThatThrownBy(record::unwrap)
                .isInstanceOf(CrawlerException.class)
                .hasMessageContaining("className");
    }

    @Test
    void constructor_withObject_setsFieldsCorrectly() {
        var entry = new CrawlEntry("ref://item");
        var record = new SerializedRecord(entry);

        assertThat(record.getClassName()).isEqualTo(CrawlEntry.class.getName());
        assertThat(record.getSerialized()).contains("ref://item");
    }

    @Test
    void roundTrip_string_value() {
        // Verify a plain String round-trips
        var record = SerializedRecord.wrap("hello world");
        String restored = record.unwrap();
        assertThat(restored).isEqualTo("hello world");
    }
}
