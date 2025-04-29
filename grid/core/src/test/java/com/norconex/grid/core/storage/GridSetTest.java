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
package com.norconex.grid.core.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import com.norconex.grid.core.cluster.ClusterExtension;
import com.norconex.grid.core.cluster.SingleNodeTest;

public abstract class GridSetTest {

    @SingleNodeTest
    void testForEachBiPredicate(GridSet set) {
        set.add("abc");
        set.add("def");
        set.add("ghi");

        // read all
        List<String> allEntries = new ArrayList<>();
        var allResp = set.forEach((k, v) -> {
            allEntries.add(k + v);
            return true;
        });
        assertThat(allResp).isTrue();
        assertThat(allEntries).containsExactlyInAnyOrder(
                "nullabc", "nulldef", "nullghi");

        // read some
        List<String> someEntries = new ArrayList<>();
        var someResp = set.forEach((k, v) -> {
            someEntries.add(k + v);
            return false;
        });
        assertThat(someResp).isFalse();
        assertThat(someEntries.size()).isEqualTo(1);
    }

    @SingleNodeTest
    void testAdd(GridSet set) {
        assertThat(set.add("abc")).isTrue();
        assertThat(set.add("abc")).isFalse();
    }

    @SingleNodeTest
    void testForEachPredicate(GridSet set) {
        set.add("abc");
        set.add("def");
        set.add("ghi");

        // read all
        List<String> allEntries = new ArrayList<>();
        var allResp = set.forEach(v -> {
            allEntries.add(v);
            return true;
        });
        assertThat(allResp).isTrue();
        assertThat(allEntries).containsExactlyInAnyOrder("abc", "def", "ghi");

        // read some
        List<String> someEntries = new ArrayList<>();
        var someResp = set.forEach(v -> {
            someEntries.add(v);
            return false;
        });
        assertThat(someResp).isFalse();
        assertThat(someEntries.size()).isEqualTo(1);
    }

    @SingleNodeTest
    void testGetName(GridSet set) {
        assertThat(set.getName()).startsWith(ClusterExtension.SET_STORE_PREFIX);
    }

    @SingleNodeTest
    void testGetType(GridSet set) {
        assertThat(set.getType()).isEqualTo(String.class);
    }

    @SingleNodeTest
    void testContains(GridSet set) {
        assertThat(set.contains("abc")).isFalse();
        set.add("abc");
        assertThat(set.contains("abc")).isTrue();
    }

    @SingleNodeTest
    void testSize(GridSet set) {
        assertThat(set.size()).isZero();
        set.add("abc");
        set.add("def");
        assertThat(set.size()).isEqualTo(2);
        // adding under an existing key should not change the size
        set.add("abc");
        set.add("def");
        assertThat(set.size()).isEqualTo(2);
    }

    @SingleNodeTest
    void testClear(GridSet set) {
        set.add("abc");
        set.add("def");
        assertThat(set.size()).isEqualTo(2);
        set.clear();
        assertThat(set.size()).isZero();
    }

    @SingleNodeTest
    void testIsEmpty(GridSet set) {
        assertThat(set.isEmpty()).isTrue();
        set.add("abc");
        set.add("def");
        assertThat(set.isEmpty()).isFalse();
    }
}
