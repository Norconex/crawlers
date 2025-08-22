package com.norconex.crawler.core2.cluster.impl.infinispan;

import java.io.Serializable;

import lombok.Builder;
import lombok.Value;

/**
 * Policy controlling runOnAllOnce quorum, heartbeat and retention behavior.
 */
@Value
@Builder(toBuilder = true)
public class AllOncePolicy implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Minimum number of successful node results required to consider task successful. */
    @Builder.Default
    int minSuccesses = 1;
    /** Require all snapshot members to respond (success or failure) before finalization. */
    @Builder.Default
    boolean requireAll = false;
    /** When quorum reached and not requireAll, finalize after this idle period with no new results. */
    @Builder.Default
    long idleResultWaitMs = 5000;
    /** Heartbeat emission interval while task executing on a node. 0 disables heartbeats. */
    @Builder.Default
    long heartbeatIntervalMs = 10000;
    /** Consider a node stale (failed) if no heartbeat for this period while still pending. */
    @Builder.Default
    long staleHeartbeatMs = 60000;
    /** Fail whole task if zero successes at end. */
    @Builder.Default
    boolean failIfZeroSuccess = true;
    /** Retention period for per-node intermediate keys (results, errors, heartbeats) after finalization. */
    @Builder.Default
    long retentionMs = 10 * 60 * 1000; // 10 minutes

    public static AllOncePolicy defaults() {
        return AllOncePolicy.builder().build();
    }
}
