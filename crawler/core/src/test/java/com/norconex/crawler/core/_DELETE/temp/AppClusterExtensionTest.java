package com.norconex.crawler.core._DELETE.temp;

import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;

@AppCluster(
    numInstances = 2,
    mainClass = "com.norconex.crawler.core.junit.cluster.DummyMain"
)
class AppClusterExtensionTest {

    @Test
    void containersShouldBeRunning(List<GenericContainer<?>> containers) {
        Assertions.assertThat(containers)
                .hasSize(2)
                .allSatisfy(container -> Assertions
                        .assertThat(container.isRunning()).isTrue());
    }

    @AppCluster(
        numInstances = 1,
        mainClass = "com.norconex.crawler.core.junit.cluster.DummyMain"
    )
    @Test
    void methodLevelAnnotationWorks(List<GenericContainer<?>> containers) {
        Assertions.assertThat(containers)
                .hasSize(1)
                .allSatisfy(container -> Assertions
                        .assertThat(container.isRunning()).isTrue());
    }
}
