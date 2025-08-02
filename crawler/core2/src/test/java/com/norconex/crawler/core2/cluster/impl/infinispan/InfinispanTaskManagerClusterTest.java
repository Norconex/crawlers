package com.norconex.crawler.core2.cluster.impl.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.infinispan.manager.DefaultCacheManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core2.cluster.ClusterTask;
import com.norconex.crawler.core2.stubs.CrawlContextStubber;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class InfinispanTaskManagerClusterTest {

    @TempDir
    private Path tempDir;

    private DefaultCacheManager cacheManager1;
    private DefaultCacheManager cacheManager2;
    private InfinispanTaskManager node1TaskManager;
    private InfinispanTaskManager node2TaskManager;

    @BeforeEach
    void setUp() {
        // Initialize two separate cache managers for two simulated nodes
        // Each cache manager will form its own cluster if not explicitly
        // configured to join one.
        // For a true cluster simulation, you'd configure JGroups for discovery.
        // For this unit test, two separate managers are sufficient to test the
        // lock/state logic.

        var node1 = InfinispanTestUtil.multiMemoryNodesCluster();
        node1.init(
                CrawlContextStubber.crawlerContext(tempDir.resolve("node1")));
        cacheManager1 = node1.getCacheManager().vendor();
        node1TaskManager = node1.getTaskManager();
        //                new InfinispanTaskManager(
        //                new InfinispanCacheManager(cacheManager1), "Node-1");

        var node2 = InfinispanTestUtil.multiMemoryNodesCluster();
        node2.init(
                CrawlContextStubber.crawlerContext(tempDir.resolve("node2")));
        cacheManager2 = node2.getCacheManager().vendor();
        node2TaskManager = node2.getTaskManager();
        //        node2TaskManager = new InfinispanTaskManager(
        //                new InfinispanCacheManager(cacheManager2), "Node-2");

        LOG.info("Setup complete for test.");
    }

    @AfterEach
    void tearDown() {
        // Stop cache managers to release resources after each test
        if (cacheManager1 != null) {
            cacheManager1.stop();
        }
        if (cacheManager2 != null) {
            cacheManager2.stop();
        }
        LOG.info("Teardown complete for test.");
    }

    @Test
    void testRunOnOneOnceAndWait() throws Exception {
        var myTask = "MyImportantStartupTask";
        var executionCount = new AtomicInteger(0);
        var taskStartedLatch = new CountDownLatch(1);
        var taskCompletedLatch = new CountDownLatch(1);

        // Define the task to be executed
        ClusterTask taskToRun = ctx -> {
            LOG.info("Executing the actual task logic");
            executionCount.incrementAndGet(); // Increment atomic counter
            // Signal that the task has started
            taskStartedLatch.countDown();
            Sleeper.sleepSeconds(2);
            LOG.info("Task logic completed");
            // Signal that the task has completed
            taskCompletedLatch.countDown();
        };

        // Node 1 tries to run the task in a separate thread
        var t1 = new Thread(() -> {
            try {
                node1TaskManager.runOnOneOnceSync(myTask, taskToRun);
            } catch (Exception e) {
                LOG.error("Node-1 task execution failed: " + e.getMessage());
            }
        });

        // Node 2 tries to run the same task shortly after
        var t2 = new Thread(() -> {
            try {
                Thread.sleep(500); // Give Node 1 a head start
                node2TaskManager.runOnOneOnceSync(myTask, taskToRun);
            } catch (Exception e) {
                LOG.error("Node-2 task execution failed: " + e.getMessage());
            }
        });

        t1.start();
        t2.start();

        if (!taskStartedLatch.await(5, TimeUnit.SECONDS)) {
            LOG.error("Task never started within timeout");
        }

        // Wait for both threads to complete their execution attempts
        t1.join(10000); // Add a timeout for join to prevent tests from hanging
        t2.join(10000);

        LOG.info("Both nodes have finished their attempts for task: " + myTask);

        // Assertions
        // Ensure the task was executed exactly once
        assertEquals(1, executionCount.get(),
                "The task should have been executed exactly once.");

        // Ensure the task status is COMPLETED in both caches
        assertNotNull(node1TaskManager.taskStatusCache.get(myTask),
                "Task status should not be null in Node-1's cache.");
        assertEquals(TaskState.COMPLETED,
                node1TaskManager.taskStatusCache.get(myTask),
                "Task status in Node-1's cache should be COMPLETED.");

        assertNotNull(node2TaskManager.taskStatusCache.get(myTask),
                "Task status should not be null in Node-2's cache.");
        assertEquals(TaskState.COMPLETED,
                node2TaskManager.taskStatusCache.get(myTask),
                "Task status in Node-2's cache should be COMPLETED.");
    }
}
