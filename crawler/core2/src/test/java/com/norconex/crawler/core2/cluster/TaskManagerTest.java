package com.norconex.crawler.core2.cluster;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class TaskManagerTest {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @ParameterizedTest(name = "with  {index} node(s)")
    @ValueSource(ints = { 1, 2 })
    public @interface SingleAndMultiNodesTest {
    }

    @TempDir
    private Path tempDir;

    @SingleAndMultiNodesTest
    void testRunOnOneAsync(int numOfNodes) throws Exception {
    }

    @SingleAndMultiNodesTest
    void testRunOnOneOnceAsync(int numOfNodes) throws Exception {
    }

    @SingleAndMultiNodesTest
    void testRunOnOneSync(int numOfNodes) throws Exception {
    }

    @SingleAndMultiNodesTest
    void testRunOnOneOnceSync(int numOfNodes) throws Exception {
    }

    @SingleAndMultiNodesTest
    void testRunOnAllAsync(int numOfNodes) throws Exception {
    }

    @SingleAndMultiNodesTest
    void testRunOnAllSync(int numOfNodes) throws Exception {
    }

    @SingleAndMultiNodesTest
    void testRunOnAllOnceAsync(int numOfNodes) throws Exception {
    }

    @SingleAndMultiNodesTest
    void testRunOnAllOnceSync(int numOfNodes) throws Exception {
    }

}