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
package com.norconex.crawler.core._DELETE.junit.cluster_old;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.Bag;
import org.apache.commons.collections4.bag.HashBag;

import com.fasterxml.jackson.databind.JsonNode;
import com.norconex.crawler.core._DELETE.junit.cluster_old.node.NodeExecutionResult;
import com.norconex.crawler.core.cluster.impl.infinispan.CacheNames;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.junit.CrawlerExecutionResult;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.Data;

/**
 * Represents the execution result of a multi-node crawler cluster test.
 * Provides access to per-node outputs (stdout, stderr, events) and
 * aggregated cluster-wide data (caches, combined events).
 */
@Data
@Deprecated
public class ClusterExecutionResult implements CrawlerExecutionResult {

    private final List<NodeExecutionResult> nodeResults;
    private final Map<String, List<JsonNode>> caches = new HashMap<>();

    /**
     * Returns true if all nodes executed successfully.
     * @return true if exit code is zero
     */
    @Override
    public boolean isOK() {
        return nodeResults.stream().allMatch(NodeExecutionResult::isOK);
    }

    /**
     * Returns all event names from all nodes combined.
     */
    @Override
    public List<String> getEventNames() {
        return nodeResults
                .stream()
                .flatMap(n -> n.getEventNames().stream())
                .toList();
    }

    /**
     * Returns the occurrences of each event name, from all nodes.
     */
    @Override
    public Bag<String> getEventNameBag() {
        var bag = new HashBag<String>();
        nodeResults.stream()
                .flatMap(n -> n.getEventNames().stream())
                .forEach(bag::add);
        return bag;
    }

    /**
     * Returns combined stdout from all nodes.
     */
    @Override
    public String getStdOut() {
        return nodeResults.stream()
                .map(n -> "\n--- Node: " + n.getNodeName()
                        + " ---\n\n" + n.getStdOut())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n\n" + b);
    }

    /**
     * Returns combined stderr from all nodes.
     */
    @Override
    public String getStdErr() {
        return nodeResults.stream()
                .map(n -> "\n--- Node: " + n.getNodeName()
                        + " ---\n\n" + n.getStdErr())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n\n" + b);
    }

    /**
     * Returns 0 if all nodes succeeded, otherwise the first non-zero
     * exit code.
     */
    @Override
    public int getExitCode() {
        return nodeResults.stream()
                .mapToInt(NodeExecutionResult::getExitCode)
                .filter(code -> code != 0)
                .findFirst()
                .orElse(0);
    }

    /**
     * Returns the first node's result (convenience method).
     * @return first node result
     */
    public NodeExecutionResult getNode1() {
        return nodeResults.isEmpty() ? null : nodeResults.get(0);
    }

    /**
     * Returns the second node's result (convenience method).
     * @return second node result
     */
    public NodeExecutionResult getNode2() {
        return nodeResults.size() < 2 ? null : nodeResults.get(1);
    }

    /**
     * Returns the third node's result (convenience method).
     * @return third node result
     */
    public NodeExecutionResult getNode3() {
        return nodeResults.size() < 3 ? null : nodeResults.get(2);
    }

    /**
     * Gets the pipeline result from the cache (for testing).
     * @return step record
     */
    public StepRecord getPipeResult() {
        var jsonObjs = caches.get(CacheNames.PIPE_CURRENT_STEP);
        if (jsonObjs == null || jsonObjs.isEmpty()) {
            return null;
        }
        // When testing there should only be one entry so grab first.
        return SerialUtil.fromJson(
                jsonObjs.get(0).get("object"),
                StepRecord.class);
    }

    @Override
    public boolean hasErrors() {
        return nodeResults.stream().anyMatch(NodeExecutionResult::hasErrors);
    }

    @Override
    public String getFailureSummary() {
        return nodeResults.stream()
                .map(n -> "=== Node: " + n.getNodeName()
                        + " ===\n" + n.getFailureSummary())
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n\n" + b);
    }

}
