package com.norconex.crawler.core2.cluster;

import java.io.Serializable;

import lombok.Builder;
import lombok.Value;

/**
 * Policy controlling runOnAllOnce quorum, heartbeat and retention behavior.
 * Placed in API package to allow generic usage across implementations.
 */
@Value
@Builder(toBuilder = true)
public class AllOncePolicy implements Serializable {
    private static final long serialVersionUID = 1L;

    @Builder.Default int minSuccesses = 1;
    @Builder.Default boolean requireAll = false;
    @Builder.Default long idleResultWaitMs = 5000; // finalize after this idle period once quorum reached (if not requireAll)
    @Builder.Default long heartbeatIntervalMs = 10000; // 0 disables heartbeats
    @Builder.Default long staleHeartbeatMs = 60000; // pending node w/out heartbeat for this long becomes failure
    @Builder.Default boolean failIfZeroSuccess = true;
    @Builder.Default long retentionMs = 10 * 60 * 1000; // intermediate key retention

    public static AllOncePolicy defaults() { return AllOncePolicy.builder().build(); }
}
