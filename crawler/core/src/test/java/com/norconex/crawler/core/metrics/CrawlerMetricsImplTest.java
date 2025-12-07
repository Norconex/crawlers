package com.norconex.crawler.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrawlerMetricsImplTest {

    @Test
    @DisplayName("incrementCounter should update in-memory event counts")
    void incrementCounterShouldUpdateInMemoryCounts() {
        var metrics = new CrawlerMetricsImpl();

        metrics.incrementCounter("DOCUMENT_IMPORTED", 10L);
        metrics.incrementCounter("DOCUMENT_IMPORTED", 10L);

        Map<String, Long> counts = metrics.getEventCounts();

        assertThat(counts.get("DOCUMENT_IMPORTED")).isEqualTo(20L);
    }
}
