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
        clusterClient.launch(CrawlerNode.builder()
                .captures(new CaptureFlags()
                        .setCaches(true)
                        .setEvents(true)
                        .setStderr(true)
                        .setStdout(true))
                .appArg("start")
                .build(), 2);
        var exitCodes = clusterClient.waitFor().termination();
        assertThat(exitCodes)
                .as("all cluster nodes should exit successfully")
                .isNotEmpty()
                .allMatch(code -> code == 0);
        StateDbClient.get().printStreamsOrderedByNode();
    }

    @Test
    void testStateDbWithoutNodes(ClusterClient clusterClient) {
        var state = clusterClient.getStateDb();
        state.put("topicA", "keyA", "valueA");
        assertThat(state.getLatest("unittest", "topicA", "keyA").orElse(null))
                .isEqualTo("valueA");
    }
}
