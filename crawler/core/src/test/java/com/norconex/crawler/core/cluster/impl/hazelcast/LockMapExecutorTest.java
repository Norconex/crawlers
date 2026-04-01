/* Copyright 2025 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.crawler.core.cluster.impl.hazelcast;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.norconex.crawler.core.junit.annotations.SlowTest;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import com.norconex.crawler.core.junit.annotations.SlowTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import com.norconex.crawler.core.junit.annotations.SlowTest;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.ClusterException;
import com.norconex.crawler.core.junit.annotations.SlowTest;
import com.norconex.crawler.core.junit.annotations.SlowTest;

@Timeout(30)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SlowTest
class LockMapExecutorTest {

    private HazelcastInstance hz;

    @BeforeAll
    void startHazelcast() {
        hz = HazelcastTestSupport.startNode();
    }

    @AfterAll
    void stopHazelcast() {
        Hazelcast.shutdownAll();
    }

    @Test
    void testExecuteWithLock_runsAction() {
        var executor =
                new LockMapExecutor(hz, "lock-map-1", Duration.ofSeconds(10));
        var ran = new AtomicBoolean(false);
        executor.executeWithLock("key1", () -> ran.set(true));
        assertThat(ran.get()).isTrue();
    }

    @Test
    void testExecuteWithLock_returnsValue() {
        var executor =
                new LockMapExecutor(hz, "lock-map-2", Duration.ofSeconds(10));
        var result = executor.executeWithLock("key2", () -> "result");
        assertThat(result).isEqualTo("result");
    }

    @Test
    void testExecuteWithLock_locksAndUnlocks() {
        var executor =
                new LockMapExecutor(hz, "lock-map-3", Duration.ofSeconds(10));
        // After the action completes, the lock should be released
        executor.executeWithLock("key3", () -> {});

        // Second call should succeed
        assertThatCode(() -> executor.executeWithLock("key3", () -> {}))
                .doesNotThrowAnyException();
    }

    @Test
    void testExecuteWithLock_throwsWhenAlreadyLocked() {
        // Simulate a "already locked" condition by using the same key
        // from two sequential calls where the first doesn't release
        var lockMap = hz.<String, Long>getMap("lock-map-4");
        // Pre-populate the lock key as if someone holds it (not expired)
        lockMap.put("key4", System.currentTimeMillis());

        var executor =
                new LockMapExecutor(hz, "lock-map-4", Duration.ofSeconds(10));
        assertThatThrownBy(() -> executor.executeWithLock("key4", () -> {}))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Lock already held");

        // Cleanup
        lockMap.remove("key4");
    }

    @Test
    void testExecuteWithLockOrSkip_skipsWhenAlreadyLocked() {
        var lockMap = hz.<String, Long>getMap("lock-map-5");
        // Pre-populate the lock key to simulate held lock (not expired)
        lockMap.put("key5", System.currentTimeMillis());

        var executor =
                new LockMapExecutor(hz, "lock-map-5", Duration.ofSeconds(10));
        var ran = new AtomicBoolean(false);
        // Should skip silently, not throw
        assertThatCode(() -> executor.executeWithLockOrSkip(
                "key5", () -> ran.set(true)))
                        .doesNotThrowAnyException();
        assertThat(ran.get()).isFalse();

        // Cleanup
        lockMap.remove("key5");
    }

    @Test
    void testExecuteWithLock_expiredLockIsOverridden() throws Exception {
        var lockMap = hz.<String, Long>getMap("lock-map-6");
        // Set an expired lock (1 second ago, with 0ms expiry)
        lockMap.put("key6", System.currentTimeMillis() - 5000);

        // Use a 1ms expiry so 5 second old lock should be expired
        var executor =
                new LockMapExecutor(hz, "lock-map-6", Duration.ofMillis(1));
        var ran = new AtomicBoolean(false);
        assertThatCode(() -> executor.executeWithLock(
                "key6", () -> ran.set(true)))
                        .doesNotThrowAnyException();
        assertThat(ran.get()).isTrue();
    }

    @Test
    void testExecuteWithLock_wrapsExceptionAsClusterException() {
        var executor =
                new LockMapExecutor(hz, "lock-map-7", Duration.ofSeconds(10));
        assertThatThrownBy(() -> executor.executeWithLock("key7", () -> {
            throw new Exception("inner error");
        })).isInstanceOf(ClusterException.class);
    }
}
