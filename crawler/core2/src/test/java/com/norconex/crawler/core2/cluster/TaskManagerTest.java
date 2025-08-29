package com.norconex.crawler.core2.cluster;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.CrawlConfig;
import com.norconex.crawler.core2.cluster.ClusterContinuousTask.WorkResult;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanClusterConnector;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanTaskManager;
import com.norconex.crawler.core2.cluster.impl.infinispan.InfinispanUtil;
import com.norconex.crawler.core2.mocks.crawler.MockCrawlDriverFactory;
import com.norconex.crawler.core2.session.CrawlSession;
import com.norconex.crawler.core2.session.CrawlSessionFactory;

@Timeout(60)
class TaskManagerTest {

    private final List<CrawlSession> sessions = new ArrayList<>();

    @AfterEach
    void tearDown() {
        sessions.forEach(
                s -> assertThatCode(s::close).doesNotThrowAnyException());
        sessions.clear();
    }

    private CrawlSession newSession(int retentionMs) {
        var cfg = new CrawlConfig();
        cfg.setId("crawler-" + UUID.randomUUID());

        var connector = new InfinispanClusterConnector();
        // configure minimal retention for quick cleanup in tests
        var icfg = connector.getConfiguration();
        icfg.setInfinispan(InfinispanUtil.configBuilderHolder(
                "/cache/infinispan-cluster-test.xml"));
        icfg.getRetention().setOnceTaskRetention(
                retentionMs <= 0 ? null : Duration.ofMillis(retentionMs));
        cfg.setClusterConnector(connector);
        var session = CrawlSessionFactory
                .create(MockCrawlDriverFactory.create(), cfg);
        sessions.add(session);
        return session;
    }

    @Test
    @DisplayName(
        "runOnOneOnce executes only once across multiple invocations on same node"
    )
    void testRunOnOneOnceSingleNode() {
        var session = newSession(0);
        var tm = session.getCluster().getTaskManager();
        var counter = new AtomicInteger();
        ClusterTask<Integer> task = s -> counter.incrementAndGet();
        var r1 = tm.runOnOneOnceSync("onlyOnceTask", task);
        var r2 = tm.runOnOneOnceSync("onlyOnceTask", task);
        assertThat(r1).contains(1);
        assertThat(r2).contains(1);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("runOnOne executes multiple generations")
    void testRunOnOneGenerations() {
        var session = newSession(0);
        var tm = session.getCluster().getTaskManager();
        var counter = new AtomicInteger();
        ClusterTask<Integer> task = s -> counter.incrementAndGet();
        for (var i = 1; i <= 3; i++) {
            var r = tm.runOnOneSync("repeatTask", task);
            assertThat(r).contains(i);
        }
        assertThat(counter.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("runOnAllOnce reduces results from all participants")
    void testRunOnAllOnceMultiNode() throws Exception {
        var s1 = newSession(0);
        var s2 = newSession(0); // join second node
        var tm1 = s1.getCluster().getTaskManager();
        var tm2 = s2.getCluster().getTaskManager();
        ClusterTask<Integer> t = sess -> 1;
        ClusterReducer<Integer, Integer> sum =
                list -> list.stream().mapToInt(i -> i).sum();
        var f1 = tm1.runOnAllOnceAsync("allOnceTask", t, sum);
        var f2 = tm2.runOnAllOnceAsync("allOnceTask", t, sum);
        assertThat(f1.get()).isEqualTo(2);
        assertThat(f2.get()).isEqualTo(2);
    }

    @Test
    @DisplayName(
        "runOnOneOnce executed on first node; late joiner retrieves cached result without re-execution"
    )
    void testRunOnOneOnceLateJoiner() {
        var s1 = newSession(0);
        var tm1 = s1.getCluster().getTaskManager();
        var counter = new AtomicInteger();
        ClusterTask<Integer> once = sess -> counter.incrementAndGet();
        assertThat(tm1.runOnOneOnceSync("lateOnce", once)).contains(1);
        assertThat(counter.get()).isEqualTo(1);
        // Late join second node (same connector config -> new cluster node)
        var s2 = newSession(0);
        var tm2 = s2.getCluster().getTaskManager();
        assertThat(tm2.runOnOneOnceSync("lateOnce", once)).contains(1);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName(
        "Continuous task processes all work items and completes on idle consensus"
    )
    void testContinuousAutoCompletion() {
        var session = newSession(0);
        var tm = session.getCluster().getTaskManager();
        // Simple shared queue using session setString (not distributed queue but good enough for test)
        var work = new AtomicInteger(10);
        ClusterContinuousTask worker = s -> {
            if (work.getAndDecrement() > 0) {
                return WorkResult.workDone();
            }
            return WorkResult.noWork();
        };
        tm.startContinuous("contTest", worker);
        tm.awaitContinuousCompletion("contTest").join();
        var stats = tm.getContinuousStats("contTest");
        var totalProcessed = stats.values().stream()
                .mapToLong(ContinuousStats::getProcessed).sum();
        assertThat(totalProcessed).isGreaterThanOrEqualTo(10);
    }

    @Test
    @DisplayName("Continuous task stopContinuous triggers graceful stop")
    void testContinuousStop() throws Exception {
        var session = newSession(0);
        var tm = session.getCluster().getTaskManager();
        var started = new CountDownLatch(1);
        var processed = new AtomicInteger();
        ClusterContinuousTask worker = s -> {
            started.countDown();
            processed.incrementAndGet();
            return WorkResult.workDone();
        };
        tm.startContinuous("contStop", worker);
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
        tm.stopContinuous("contStop");
        tm.awaitContinuousCompletion("contStop").join();
        var stats = tm.getContinuousStats("contStop");
        var total = stats.values().stream()
                .mapToLong(ContinuousStats::getProcessed).sum();
        assertThat(total).isPositive();
    }

    @Test
    @DisplayName(
        "Once-task retention removes cached result after retention period"
    )
    void testOnceTaskRetentionCleanup() throws Exception {
        // set retention to 200ms
        var session = newSession(200);
        var tm = session.getCluster().getTaskManager();
        var counter = new AtomicInteger();
        tm.runOnOneOnceSync("retainedTask", s -> counter.incrementAndGet());
        assertThat(counter.get()).isEqualTo(1);
        // Access underlying caches to assert cleanup (best effort): wait > retention
        Thread.sleep(600);
        // Since key is scoped we reconstruct prefix
        var scopedName = session.getCrawlerId() + "|" + session.getSessionId()
                + "|retainedTask";
        var resultKey = scopedName + ":once:result";
        var impl = (InfinispanTaskManager) tm;
        // Expect removal (raw null) due to retention
        assertThat(impl.hasRawTaskResult(resultKey)).isFalse();
    }

    @Test
    @DisplayName(
        "runOnAll barrier executes on all nodes (single node fallback)"
    )
    void testRunOnAllSingleNode() {
        var session = newSession(0);
        var tm = session.getCluster().getTaskManager();
        var counter = new AtomicInteger();
        ClusterTask<Integer> task = s -> counter.incrementAndGet();
        var sum = tm.runOnAllSync("barrier", task,
                list -> list.stream().mapToInt(i -> i).sum());
        assertThat(sum).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("runOnAllOnce caches reduction for subsequent invocations")
    void testRunOnAllOnceCacheReuse() throws Exception {
        var s1 = newSession(0);
        var s2 = newSession(0);
        var tm1 = s1.getCluster().getTaskManager();
        var tm2 = s2.getCluster().getTaskManager();
        var counter = new AtomicInteger();
        ClusterTask<Integer> task = sess -> counter.incrementAndGet();
        ClusterReducer<Integer, Integer> sum =
                list -> list.stream().mapToInt(i -> i).sum();
        assertThat(tm1.runOnAllOnceAsync("allOnceCache", task, sum).get())
                .isEqualTo(2);
        // second invocation should reuse cached result; no new increments expected
        assertThat(tm2.runOnAllOnceAsync("allOnceCache", task, sum).get())
                .isEqualTo(2);
        assertThat(counter.get()).isEqualTo(2);
    }
}
