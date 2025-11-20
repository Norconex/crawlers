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

import java.util.List;
import java.util.Objects;

import com.norconex.crawler.core._DELETE.junit.cluster_old.node.NodeExecutionResult;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
@Deprecated
public class ClusterState {

    private final CrawlerCluster cluster;

    public List<String> propStrings(String key) {
        return cluster
                .getNodes()
                .stream()
                .map(NodeExecutionResult::loadStateProps)
                .map(props -> props.getString(key))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Integer> propInts(String key) {
        return cluster
                .getNodes()
                .stream()
                .map(NodeExecutionResult::loadStateProps)
                .map(props -> props.getInteger(key))
                .filter(Objects::nonNull)
                .toList();
    }

    public int propCount(String key) {
        return (int) cluster
                .getNodes()
                .stream()
                .map(NodeExecutionResult::loadStateProps)
                .filter(props -> props.containsKey(key))
                .count();
    }

    public int highestPropIntOrZero(String key) {
        return propInts(key).stream().max(Integer::compareTo).orElse(0);
    }

    public int lowestPropIntOrZero(String key) {
        return propInts(key).stream().min(Integer::compareTo).orElse(0);
    }
}
