package com.norconex.crawler.core.junit.tomerge_or_delete;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.function.Supplier;

import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.extension.ExtendWith;

import com.norconex.crawler.core.CrawlDriver;
import com.norconex.crawler.core.cluster.ClusterConnector;
import com.norconex.crawler.core.mocks.cluster.MockMultiNodesConnector;
import com.norconex.crawler.core.mocks.crawler.MockCrawlDriverFactory;

/**
 * Test template for running the same test logic against different numbers
 * of cluster nodes (multi-session). Each invocation creates N independent
 * {@code CrawlSession}s sharing the same cluster connector configuration,
 * thus forming a cluster of size N. All sessions are closed automatically
 * after the invocation.
 * <p>
 * Parameters that can be injected into test methods:
 * <ul>
 *   <li>{@code int} / {@code Integer}: the node count for this invocation</li>
 *   <li>{@code java.util.List<CrawlSession>} all created sessions</li>
 *   <li>{@code java.util.List<Cluster>} all clusters (parallel to sessions)</li>
 *   <li>{@code CrawlSession} the first session</li>
 *   <li>{@code Cluster} the first session's cluster</li>
 *   <li>{@code CacheManager} from the first session</li>
 *   <li>{@code TaskManager} from the first session</li>
 * </ul>
 * </p>
 */
@Retention(RUNTIME)
@Target({ ElementType.METHOD })
@TestTemplate
@ExtendWith(ClusterNodesTestExtension.class)
public @interface ClusterNodesTest {

    /** Node counts (cluster sizes) to execute the test with. */
    int[] nodes() default { 1, 2 };

    /** Cluster connector class to instantiate per session. */
    Class<? extends ClusterConnector> connector() default MockMultiNodesConnector.class;

    /** Driver factory (supplier) used to create the crawler driver. */
    Class<? extends Supplier<
            CrawlDriver>> driverFactory() default MockCrawlDriverFactory.class;

    /**
     * Infinispan XML resource path (only used when connector is Infinispan).
     */
    String infinispanConfig() default "/cache/infinispan-cluster-test.xml";

    /**
     * Infinispan node expiry timeout in milliseconds (only used when
     * connector is Infinispan). Default is 30 seconds.
     */
    long infinispanNodeExpiryTimeout() default 30_000;

    /**
     * Whether to wait for the cluster membership to reach the node count
     * before test execution.
     */
    boolean waitForMembership() default true;

    /** Timeout in seconds for waiting for cluster membership. */
    int membershipTimeoutSeconds() default 10;
}
