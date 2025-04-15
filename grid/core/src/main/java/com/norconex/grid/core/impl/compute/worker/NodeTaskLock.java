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

import org.jgroups.Address;

import com.norconex.grid.core.impl.CoreGrid;

/**
 * This class ensures that a task cannot run concurrently on the same node
 * (even if there are multiple "virtual" nodes in the same JVM) by tracking
 * active tasks in each of a JVM nodes.
 */
class NodeTaskLock implements AutoCloseable {

    private static final Map<Address, Set<String>> activeTasks =
            new ConcurrentHashMap<>();

    private final Address node;
    private final String taskName;

    private NodeTaskLock(Address node, String taskName) {
        this.node = node;
        this.taskName = taskName;
        activeTasks.compute(node, (n, set) -> {
            if (set == null)
                set = ConcurrentHashMap.newKeySet();
            set.add(taskName);
            return set;
        });
    }

    public static void runExclusively(
            CoreGrid grid, String taskName, Runnable runnable) {
        var addr = grid.getLocalAddress();
        if (isActive(addr, taskName)) {
            throw new IllegalStateException("Task '" + taskName
                    + "' is already running on this node (" + addr + ").");
        }
        try (var lock = new NodeTaskLock(addr, taskName)) {
            runnable.run();
        }
    }

    private static boolean isActive(Address node, String taskName) {
        var set = activeTasks.get(node);
        return set != null && set.contains(taskName);
    }

    @Override
    public void close() {
        activeTasks.compute(node, (n, set) -> {
            if (set != null) {
                set.remove(taskName);
                return set.isEmpty() ? null : set;
            }
            return null;
        });
    }
}
