package com.norconex.crawler.core2.cluster.impl.infinispan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.norconex.commons.lang.Sleeper;
import com.norconex.crawler.core2.cluster.ClusterTask;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.stubs.CrawlSessionStubber;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class InfinispanTaskManagerTest {

    @TempDir
    private Path tempDir;

    private InfinispanTaskManager taskManager1;
    private InfinispanTaskManager taskManager2;
    private CrawlSession session1;
    private CrawlSession session2;

    @BeforeEach
    void setUp() {
        session1 = CrawlSessionStubber
                .multiNodesCrawlSession(tempDir.resolve("node1"));
        taskManager1 = (InfinispanTaskManager) session1.getCluster()
                .getTaskManager();

        session2 = CrawlSessionStubber
                .multiNodesCrawlSession(tempDir.resolve("node2"));
        taskManager2 = (InfinispanTaskManager) session2.getCluster()
                .getTaskManager();
        
        LOG.info("Setup complete - two nodes initialized");
    }

    @AfterEach
    void tearDown() {
        session1.close();
        session2.close();
        LOG.info("Teardown complete");
    }

    @Test
    void testRunOnOneAsync() throws Exception {
        var taskName = "TestRunOnOneTask";
        var executionCount = new AtomicInteger(0);

        ClusterTask<String> task = ctx -> {
            LOG.info("Executing runOnOne task on node: {}", ctx.getCrawlerId());
            executionCount.incrementAndGet();
            Sleeper.sleepMillis(100);
            return "result-from-" + ctx.getCrawlerId();
        };

        // Both nodes try to execute the task
        var future1 = taskManager1.runOnOneAsync(taskName, task);
        var future2 = taskManager2.runOnOneAsync(taskName, task);

        // Wait for both to complete
        var result1 = future1.get(5, TimeUnit.SECONDS);
        var result2 = future2.get(5, TimeUnit.SECONDS);

        LOG.info("Node1 result: {}, Node2 result: {}", result1, result2);
        LOG.info("Total executions: {}", executionCount.get());

        // Only one node should have executed the task
        assertEquals(1, executionCount.get(), 
                "Task should execute exactly once across the cluster");

        // BOTH nodes should have a result (non-executing node gets cached result)
        assertTrue(result1.isPresent() && result2.isPresent(),
                "Both nodes should have a result (executing node and cached result)");
        
        // Results should be the same (cached result should match executed result)
        assertEquals(result1.get(), result2.get(),
                "Both nodes should have the same result");
    }

    @Test
    void testRunOnOneSync() throws Exception {
        var taskName = "TestRunOnOneSyncTask";
        var executionCount = new AtomicInteger(0);
        var completionLatch = new CountDownLatch(2);
        var results = new ConcurrentHashMap<String, Optional<String>>();

        ClusterTask<String> task = ctx -> {
            LOG.info("Executing runOnOneSync task");
            executionCount.incrementAndGet();
            Sleeper.sleepMillis(100);
            return "sync-result";
        };

        // Run on both nodes in separate threads
        var t1 = new Thread(() -> {
            try {
                var result = taskManager1.runOnOneSync(taskName, task);
                LOG.info("Node1 sync result: {}", result);
                results.put("node1", result);
                completionLatch.countDown();
            } catch (Exception e) {
                LOG.error("Node1 sync failed", e);
                completionLatch.countDown();
            }
        });

        var t2 = new Thread(() -> {
            try {
                Sleeper.sleepMillis(50); // Give node1 slight head start
                var result = taskManager2.runOnOneSync(taskName, task);
                LOG.info("Node2 sync result: {}", result);
                results.put("node2", result);
                completionLatch.countDown();
            } catch (Exception e) {
                LOG.error("Node2 sync failed", e);
                completionLatch.countDown();
            }
        });

        t1.start();
        t2.start();
        
        assertTrue(completionLatch.await(10, TimeUnit.SECONDS),
                "Both nodes should complete within timeout");

        assertEquals(1, executionCount.get(),
                "RunOnOneSync should execute exactly once");
        
        // Both nodes should have results
        assertTrue(results.get("node1").isPresent() && results.get("node2").isPresent(),
                "Both nodes should have results");
        assertEquals(results.get("node1").get(), results.get("node2").get(),
                "Both nodes should have the same result");
    }

    @Test
    void testRunOnOneOnceAsync() throws Exception {
        var taskName = "TestRunOnOneOnceTask";
        var executionCount = new AtomicInteger(0);

        ClusterTask<String> task = ctx -> {
            LOG.info("Executing runOnOneOnce task");
            executionCount.incrementAndGet();
            Sleeper.sleepMillis(100);
            return "once-result";
        };

        // First execution - both nodes try
        var future1 = taskManager1.runOnOneOnceAsync(taskName, task);
        var future2 = taskManager2.runOnOneOnceAsync(taskName, task);

        var result1 = future1.get(5, TimeUnit.SECONDS);
        var result2 = future2.get(5, TimeUnit.SECONDS);

        assertEquals(1, executionCount.get(),
                "First execution should run exactly once");
        
        // Both nodes should have results
        assertTrue(result1.isPresent() && result2.isPresent(),
                "Both nodes should have results from first execution");
        assertEquals(result1.get(), result2.get(),
                "Both nodes should have the same result from first execution");

        // Second execution attempt - should not run again but return cached result
        var future3 = taskManager1.runOnOneOnceAsync(taskName, task);
        var future4 = taskManager2.runOnOneOnceAsync(taskName, task);

        var result3 = future3.get(5, TimeUnit.SECONDS);
        var result4 = future4.get(5, TimeUnit.SECONDS);

        assertEquals(1, executionCount.get(),
                "Second execution should not run - task already completed once");

        // Both nodes should still have the cached result
        assertTrue(result3.isPresent() && result4.isPresent(),
                "Both nodes should have cached results from second call");
        assertEquals(result1.get(), result3.get(),
                "Second call should return same cached result");
        assertEquals(result2.get(), result4.get(),
                "Second call should return same cached result");

        // Verify task status is COMPLETED
        assertEquals(TaskState.COMPLETED, 
                taskManager1.taskStatusCache.get(taskName + ":once"),
                "Task status should be COMPLETED");
    }

    @Test
    void testRunOnOneOnceSync() throws Exception {
        var taskName = "TestRunOnOneOnceSyncTask";
        var executionCount = new AtomicInteger(0);
        var completionLatch = new CountDownLatch(2);

        ClusterTask<String> task = ctx -> {
            LOG.info("Executing runOnOneOnceSync task");
            executionCount.incrementAndGet();
            Sleeper.sleepMillis(200);
            return "once-sync-result";
        };

        // First execution
        var t1 = new Thread(() -> {
            try {
                taskManager1.runOnOneOnceSync(taskName, task);
                completionLatch.countDown();
            } catch (Exception e) {
                LOG.error("Node1 failed", e);
                completionLatch.countDown();
            }
        });

        var t2 = new Thread(() -> {
            try {
                Sleeper.sleepMillis(50);
                taskManager2.runOnOneOnceSync(taskName, task);
                completionLatch.countDown();
            } catch (Exception e) {
                LOG.error("Node2 failed", e);
                completionLatch.countDown();
            }
        });

        t1.start();
        t2.start();

        assertTrue(completionLatch.await(10, TimeUnit.SECONDS),
                "Both nodes should complete within timeout");

        assertEquals(1, executionCount.get(),
                "Task should execute exactly once");

        // Try running again - should not execute
        var result = taskManager1.runOnOneOnceSync(taskName, task);
        assertEquals(1, executionCount.get(),
                "Task should still have executed only once after second attempt");
    }

    @Test
    void testRunOnAllAsync() throws Exception {
        var taskName = "TestRunOnAllTask";
        var executionCount = new AtomicInteger(0);

        ClusterTask<String> task = ctx -> {
            LOG.info("Executing runOnAll task on node");
            executionCount.incrementAndGet();
            return "all-result";
        };

        // Both nodes should execute
        var future1 = taskManager1.runOnAllAsync(taskName, task, results -> {
            LOG.info("Reducer called with {} results", results.size());
            return results.size();
        });

        // Wait for completion
        var finalResult = future1.get(10, TimeUnit.SECONDS);

        assertEquals(2, executionCount.get(),
                "Task should execute on all nodes (2)");
        assertEquals(2, finalResult,
                "Reducer should receive results from both nodes");

        // Run again - should work (not "once")
        executionCount.set(0); // Reset counter
        var future2 = taskManager2.runOnAllAsync(taskName + "2", task, results -> results.size());
        var secondResult = future2.get(10, TimeUnit.SECONDS);

        assertEquals(2, executionCount.get(),
                "Second execution should also run on all nodes");
        assertEquals(2, secondResult,
                "Second execution should also return 2 results");
    }

    @Test
    void testRunOnAllSync() throws Exception {
        var taskName = "TestRunOnAllSyncTask";
        var executionCount = new AtomicInteger(0);

        ClusterTask<Integer> task = ctx -> {
            LOG.info("Executing runOnAllSync task");
            return executionCount.incrementAndGet();
        };

        // Execute on one node - should run on all
        var result = taskManager1.runOnAllSync(taskName, task, results -> {
            LOG.info("Sync reducer got {} results: {}", results.size(), results);
            return results.stream().mapToInt(Integer::intValue).sum();
        });

        assertEquals(2, executionCount.get(),
                "Task should execute on both nodes");
        assertTrue(result > 0, "Reducer should return sum of results");
    }

    @Test
    void testRunOnAllOnceAsync() throws Exception {
        var taskName = "TestRunOnAllOnceTask";
        var executionCount = new AtomicInteger(0);

        ClusterTask<String> task = ctx -> {
            LOG.info("Executing runOnAllOnce task");
            executionCount.incrementAndGet();
            return "all-once-result";
        };

        // First execution
        var future1 = taskManager1.runOnAllOnceAsync(taskName, task, results -> {
            LOG.info("First reducer got {} results", results.size());
            return results.size();
        });

        var result1 = future1.get(10, TimeUnit.SECONDS);

        assertEquals(2, executionCount.get(),
                "First execution should run on both nodes");
        assertEquals(2, result1,
                "First execution should return 2 results");

        // Second execution - should not run again
        var future2 = taskManager2.runOnAllOnceAsync(taskName, task, results -> {
            LOG.info("Second reducer got {} results", results.size());
            return results.size();
        });

        var result2 = future2.get(10, TimeUnit.SECONDS);

        assertEquals(2, executionCount.get(),
                "Second execution should not run - task already completed once");
        assertEquals(2, result2,
                "Should still return the original results");
    }

    @Test
    void testRunOnAllOnceSync() throws Exception {
        var taskName = "TestRunOnAllOnceSyncTask";
        var executionCount = new AtomicInteger(0);

        ClusterTask<Integer> task = ctx -> {
            LOG.info("Executing runOnAllOnceSync task");
            return executionCount.incrementAndGet();
        };

        // First execution
        var result1 = taskManager1.runOnAllOnceSync(taskName, task, 
                results -> results.stream().mapToInt(Integer::intValue).sum());

        assertEquals(2, executionCount.get(),
                "First execution should run on both nodes");
        assertTrue(result1 > 0, "Should get sum of results");

        // Second execution - should not run
        var result2 = taskManager2.runOnAllOnceSync(taskName, task,
                results -> results.stream().mapToInt(Integer::intValue).sum());

        assertEquals(2, executionCount.get(),
                "Second execution should not increase count");
        assertEquals(result1, result2,
                "Should return same result as first execution");
    }

    private void waitForTaskCompletion(String taskName, long timeoutSeconds) 
            throws InterruptedException {
        long start = System.currentTimeMillis();
        long timeout = timeoutSeconds * 1000;
        
        while (System.currentTimeMillis() - start < timeout) {
            var status = taskManager1.taskStatusCache.get(taskName);
            if (TaskState.COMPLETED.equals(status) || TaskState.FAILED.equals(status)) {
                return;
            }
            Thread.sleep(100);
        }
        
        throw new RuntimeException("Task did not complete within timeout");
    }
}