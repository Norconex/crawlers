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
package com.norconex.grid.core.impl.compute.worker;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.jgroups.Address;

import com.norconex.grid.core.compute.GridJobState;
import com.norconex.grid.core.impl.CoreGrid;

/**
 * This class ensures that a job cannot run concurrently on the same node
 * (even if there are multiple "virtual" nodes in the same JVM) by tracking
 * active jobs in each of a JVM nodes.
 */
class NodeJobLock implements AutoCloseable {

    private static final Map<Address, Set<String>> activeJobs =
            new ConcurrentHashMap<>();

    private final Address node;
    private final String jobName;

    private NodeJobLock(Address node, String jobName) {
        this.node = node;
        this.jobName = jobName;
        activeJobs.compute(node, (n, set) -> {
            if (set == null)
                set = ConcurrentHashMap.newKeySet();
            set.add(jobName);
            return set;
        });
    }

    public static GridJobState runExclusively(
            CoreGrid grid, String jobName, Supplier<GridJobState> supplier) {
        var addr = grid.getLocalAddress();
        if (isActive(addr, jobName)) {
            throw new IllegalStateException("Job '" + jobName
                    + "' is already running on this node (" + addr + ").");
        }
        try (var lock = new NodeJobLock(addr, jobName)) {
            return supplier.get();
        }
    }

    private static boolean isActive(Address node, String jobName) {
        var set = activeJobs.get(node);
        return set != null && set.contains(jobName);
    }

    @Override
    public void close() {
        activeJobs.compute(node, (n, set) -> {
            if (set != null) {
                set.remove(jobName);
                return set.isEmpty() ? null : set;
            }
            return null;
        });
    }
}
