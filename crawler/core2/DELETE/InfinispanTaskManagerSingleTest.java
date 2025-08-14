package com.norconex.crawler.core2.cluster.impl.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.io.TempDir;

import com.norconex.crawler.core2.cluster.ClusterTask;
import com.norconex.crawler.core2.cluster.TaskManager;
import com.norconex.crawler.core2.junit.CrawlTest;
import com.norconex.crawler.core2.junit.CrawlTest.Focus;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class InfinispanTaskManagerSingleTest {

    @TempDir
    private Path tempDir;

    //    private DefaultCacheManager cacheManager;
    //    private InfinispanTaskManager taskManager;
    //
    //    @BeforeEach
    //    void setUp() {
    //        var node = InfinispanTestUtil.singleMemoryNodeCluster();
    //        node.init(tempDir.resolve("nodesingle"));
    //        //        node.init(CrawlContextStubber.crawlerContext(
    //        //                tempDir.resolve("nodesingle")));
    //        //                tempDir.resolve("nodesingle"));
    //        cacheManager = node.getCacheManager().vendor();
    //        taskManager = node.getTaskManager();
    //        //                new InfinispanTaskManager(
    //        //                new InfinispanCacheManager(cacheManager), "Node-Single");
    //        LOG.info("Setup complete for test.");
    //    }
    //
    //    @AfterEach
    //    void tearDown() {
    //        // Stop cache managers to release resources after each test
    //        if (cacheManager != null) {
    //            cacheManager.stop();
    //        }
    //        LOG.info("Teardown complete for test.");
    //    }

    @CrawlTest(focus = Focus.SESSION)
    void testRunOnOneOnceAndWait(TaskManager taskManager) throws Exception {
        var infiniTaskManager = (InfinispanTaskManager) taskManager;
        var myTask = "MyImportantStartupTask";
        var executionCount = new AtomicInteger(0);
        var taskStartedLatch = new CountDownLatch(1);
        var taskCompletedLatch = new CountDownLatch(1);

        // Define the task to be executed
        ClusterTask<Void> taskToRun = ctx -> {
            LOG.info("Executing the actual task logic");
            executionCount.incrementAndGet(); // Increment atomic counter
            // Signal that the task has started
            taskStartedLatch.countDown();
            LOG.info("Task logic completed");
            // Signal that the task has completed
            taskCompletedLatch.countDown();
            return null;
        };

        // Node tries to run the task in a separate thread
        var t = new Thread(() -> {
            try {
                infiniTaskManager.runOnOneOnceSync(myTask, taskToRun);
            } catch (Exception e) {
                LOG.error("Node-Single task execution failed.", e);
            }
        });

        t.start();

        if (!taskStartedLatch.await(5, TimeUnit.SECONDS)) {
            LOG.error("Task never started within timeout");
        }

        LOG.info("Single node has finished its attempts for task: " + myTask);

        // Assertions
        // Ensure the task was executed exactly once
        assertEquals(1, executionCount.get(),
                "The task should have been executed exactly once.");

        // Ensure the task status is COMPLETED in both caches
        assertNotNull(infiniTaskManager.taskStatusCache.get(myTask),
                "Task status should not be null in Node-Single's cache.");
        assertEquals(TaskState.COMPLETED,
                infiniTaskManager.taskStatusCache.get(myTask),
                "Task status in Node-Single's cache should be COMPLETED.");
    }
}
