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

public abstract class GridMapTest extends AbstractGridTest {

    private Grid grid;
    private GridMap<String> map;

    @BeforeEach
    void beforeEach() {
        grid = getGridConnector().connect(getTempDir());
        map = grid.storage().getMap("testMap", String.class);
    }

    @AfterEach
    void afterEach() {
        map.clear();
        grid.close();
    }

    @Test
    void testPut() {
        assertThat(map.put("abc", "123")).isTrue();
        assertThat(map.get("abc")).isEqualTo("123");

        // adding again should return false
        assertThat(map.put("abc", "123")).isFalse();

        // adding again with different value should return true
        assertThat(map.put("abc", "456")).isTrue();
    }

    @Test
    void testUpdate() {
        // first with nothing to update, should just add
        assertThat(map.update(
                "abc", v -> v == null ? "123" : v + "123")).isTrue();
        assertThat(map.get("abc")).isEqualTo("123");

        // with existing value, we append it
        assertThat(map.update(
                "abc", v -> v == null ? "123" : v + "123")).isTrue();
        assertThat(map.get("abc")).isEqualTo("123123");
    }

    @Test
    void testDelete() {
        map.put("abc", "123");
        map.put("def", "456");
        assertThat(map.size()).isEqualTo(2);
        assertThat(map.get("abc")).isEqualTo("123");
        assertThat(map.get("def")).isEqualTo("456");

        assertThat(map.delete("abc")).isTrue();
        assertThat(map.size()).isEqualTo(1);
        assertThat(map.get("abc")).isNull();
        assertThat(map.get("def")).isEqualTo("456");

        // trying to delete again should return false
        assertThat(map.delete("abc")).isFalse();
    }

    @Test
    void testGetName() {
        assertThat(map.getName()).isEqualTo("testMap");
    }

    @Test
    void testGetType() {
        assertThat(map.getType()).isEqualTo(String.class);
    }

    @Test
    void testContains() {
        assertThat(map.contains("abc")).isFalse();
        map.put("abc", "123");
        assertThat(map.contains("abc")).isTrue();
    }

    @Test
    void testSize() {
        assertThat(map.size()).isZero();
        map.put("abc", "123");
        map.put("def", "456");
        assertThat(map.size()).isEqualTo(2);
        // adding under an existing key should not change the size
        map.put("abc", "123");
        map.put("def", "789");
        assertThat(map.size()).isEqualTo(2);
    }

    @Test
    void testClear() {
        map.put("abc", "123");
        map.put("def", "456");
        assertThat(map.size()).isEqualTo(2);
        map.clear();
        assertThat(map.size()).isZero();
    }

    @Test
    void testForEach() {
        map.put("abc", "123");
        map.put("def", "456");
        map.put("ghi", "789");

        // read all
        List<String> allEntries = new ArrayList<>();
        var allResp = map.forEach((k, v) -> {
            allEntries.add(k + v);
            return true;
        });
        assertThat(allResp).isTrue();
        assertThat(allEntries).isEqualTo(List.of("abc123", "def456", "ghi789"));

        // read some
        List<String> someEntries = new ArrayList<>();
        var someResp = map.forEach((k, v) -> {
            someEntries.add(k + v);
            return false;
        });
        assertThat(someResp).isFalse();
        assertThat(someEntries).isEqualTo(List.of("abc123"));

    }

    @Test
    void testIsEmpty() {
        assertThat(map.isEmpty()).isTrue();
        map.put("abc", "123");
        map.put("def", "456");
        assertThat(map.isEmpty()).isFalse();
    }
}
