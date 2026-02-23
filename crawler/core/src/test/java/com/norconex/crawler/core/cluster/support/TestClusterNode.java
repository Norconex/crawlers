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
package com.norconex.crawler.core.cluster.support;

import com.norconex.crawler.core.cluster.ClusterNode;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Simple configurable {@link ClusterNode} stub for use in unit tests.
 */
@Data
@Accessors(chain = true)
public class TestClusterNode implements ClusterNode {
    private String nodeName = "test-node";
    private boolean coordinator = true;

    @Override
    public boolean isCoordinator() {
        return coordinator;
    }
}
