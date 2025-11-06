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
package com.norconex.crawler.core.junit.cluster;

import java.util.List;
import java.util.Objects;

import com.norconex.crawler.core.junit.cluster.node.CrawlerNode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ClusterState {

    private final CrawlerCluster cluster;

    public List<String> nodeStrings(String key) {
        return cluster
                .getNodes()
                .stream()
                .map(CrawlerNode::loadStateProps)
                .map(props -> props.getString(key))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<Integer> nodeInts(String key) {
        return cluster
                .getNodes()
                .stream()
                .map(CrawlerNode::loadStateProps)
                .map(props -> props.getInteger(key))
                .filter(Objects::nonNull)
                .toList();
    }

    public int highestNodeIntOrZero(String key) {
        return nodeInts(key).stream().max(Integer::compareTo).orElse(0);
    }

    public int lowestNodeIntOrZero(String key) {
        return nodeInts(key).stream().min(Integer::compareTo).orElse(0);
    }

    //    public Map<Integer, Properties> loadNodeStates() {
    //
    //        //        try {
    //        //            Files.list(workDir).forEach(file -> {
    //        //                var name = file.getFileName().toString();
    //        //                if (name.endsWith(CrawlerNodeState.FILE_SUFFIX)) {
    //        //                    var nodeIndex = Integer
    //        //                            .parseInt(name.replaceFirst("^(\\d+).*", "$1"));
    //        //                    nodeStates.put(nodeIndex, CrawlerNodeState.load(file));
    //        //                }
    //        //            });
    //        //        } catch (IOException e) {
    //        //            throw new UncheckedIOException(e);
    //        //        }
    //        return new HashMap<>();
    //    }
    //
    //    public Properties loadNodeState(int nodeIndex) {
    //        return NodeState.load(workDir);
    //    }

    // wait for all values to be...
    // wait for one value to be...
}
