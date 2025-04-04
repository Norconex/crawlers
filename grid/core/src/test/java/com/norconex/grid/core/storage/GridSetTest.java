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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.grid.core.AbstractGridTest;
import com.norconex.grid.core.Grid;

public abstract class GridSetTest extends AbstractGridTest {

    private Grid grid;
    private GridSet set;

    @BeforeEach
    void beforeEach() {
        grid = getGridConnector().connect(getTempDir());
        set = grid.storage().getSet("testSet");
    }

    @AfterEach
    void afterEach() {
        set.clear();
        grid.close();
    }

    @Test
    void testForEachBiPredicate() {
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

    @Test
    void testAdd() {
        assertThat(set.add("abc")).isTrue();
        assertThat(set.add("abc")).isFalse();
    }

    @Test
    void testForEachPredicate() {
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

    @Test
    void testGetName() {
        assertThat(set.getName()).isEqualTo("testSet");
    }

    @Test
    void testGetType() {
        assertThat(set.getType()).isEqualTo(String.class);
    }

    @Test
    void testContains() {
        assertThat(set.contains("abc")).isFalse();
        set.add("abc");
        assertThat(set.contains("abc")).isTrue();
    }

    @Test
    void testSize() {
        assertThat(set.size()).isZero();
        set.add("abc");
        set.add("def");
        assertThat(set.size()).isEqualTo(2);
        // adding under an existing key should not change the size
        set.add("abc");
        set.add("def");
        assertThat(set.size()).isEqualTo(2);
    }

    @Test
    void testClear() {
        set.add("abc");
        set.add("def");
        assertThat(set.size()).isEqualTo(2);
        set.clear();
        assertThat(set.size()).isZero();
    }

    @Test
    void testIsEmpty() {
        assertThat(set.isEmpty()).isTrue();
        set.add("abc");
        set.add("def");
        assertThat(set.isEmpty()).isFalse();
    }
}
