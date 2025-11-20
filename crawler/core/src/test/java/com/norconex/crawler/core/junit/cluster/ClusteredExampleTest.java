package com.norconex.crawler.core.junit.cluster;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.crawler.core.junit.cluster.node.CaptureFlags;
import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;
import com.norconex.crawler.core.junit.cluster.state.StateDbClient;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Clustered
class ClusteredExampleTest {
    @Test
    void testStateDbWithNodes(ClusterClient clusterClient) {
        clusterClient.launch(2, CrawlerNode.builder()
                .captures(new CaptureFlags()
                        .setCaches(true)
                        .setEvents(true)
                        .setStderr(true)
                        .setStdout(true))
                .appArg("start")
                .build());
        clusterClient.waitFor().termination();

        StateDbClient.get().getStdoutForAllNodes().forEach(rec -> {
            LOG.info("XXX MSG ENTRY: {}", rec);
        });
        StateDbClient.get().getStderrForAllNodes().forEach(rec -> {
            LOG.error("XXX MSG ENTRY: {}", rec);
        });

    }

    @Test
    void testStateDbWithoutNodes(ClusterClient clusterClient) {
        var state = clusterClient.getStateDb();
        state.put("topicA", "keyA", "valueA");
        assertThat(state.getLatest("unittest", "topicA", "keyA").orElse(null))
                .isEqualTo("valueA");
    }
}
