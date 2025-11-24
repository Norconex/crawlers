package com.norconex.crawler.core.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CrawlerMetricsImplTest {

    @Test
    @DisplayName(
        "batchIncrementCounter should accumulate values in local batch"
    )
    void batchIncrementShouldAccumulateLocally() throws Exception {
        var metrics = new CrawlerMetricsImpl();

        metrics.batchIncrementCounter("DOCUMENT_IMPORTED", 10L);
        metrics.batchIncrementCounter("DOCUMENT_IMPORTED", 10L);

        // We cannot call flushBlocking() here since it requires a
        // non-null cluster cache, which is only available after
        // init(CrawlSession). Instead, we validate the local batch
        // behavior that feeds the eventual flush.
        var field = CrawlerMetricsImpl.class
                .getDeclaredField("eventCountsLocalBatch");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var localBatch = (java.util.concurrent.ConcurrentHashMap<String,
                Long>) field.get(metrics);

        assertThat(localBatch.get("DOCUMENT_IMPORTED")).isEqualTo(20L);
    }
}
