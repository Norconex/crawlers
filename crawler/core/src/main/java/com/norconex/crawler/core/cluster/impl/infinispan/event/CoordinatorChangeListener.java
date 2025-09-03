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
package com.norconex.crawler.core.cluster.impl.infinispan.event;

/**
 * Listen for coordinator change affecting only this node.
 */
@FunctionalInterface
public interface CoordinatorChangeListener {
    /**
     * Invoked when the coordinator changes, only if it means this node
     * either becomes the coordinator, or stop being the coordinator.
     * If the coordinator role jumps from one other node to another node and
     * this one remains a non-coordinator in the process, the method is not
     * invoked.
     * @param isCoordinator is this node the coordinator now
     */
    void onCoordinatorChange(boolean isCoordinator);
}
