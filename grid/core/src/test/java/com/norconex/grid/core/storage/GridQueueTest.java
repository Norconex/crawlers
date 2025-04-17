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
import com.norconex.grid.core.mocks.MockGridName;

public abstract class GridQueueTest extends AbstractGridTest {

    private Grid grid;
    private GridQueue<String> queue;

    @BeforeEach
    void beforeEachQueueTest() {
        grid = getGridConnector(MockGridName.generate())
                .connect(getTempDir());
        queue = grid.storage().getQueue("testQueue", String.class);
    }

    @AfterEach
    void afterEachQueueTest() {
        queue.clear();
        grid.storage().destroy();
        grid.close();
    }

    @Test
    void testPutPoll() {
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isZero();

        assertThat(queue.put("abc", "123")).isTrue();
        // should not add if already exists with same key
        assertThat(queue.put("abc", "321")).isFalse();
        assertThat(queue.put("def", "456")).isTrue();
        assertThat(queue.put("ghi", "789")).isTrue();

        assertThat(queue.isEmpty()).isFalse();
        assertThat(queue.size()).isEqualTo(3);

        String value;

        value = queue.poll().orElse(null);
        assertThat(queue.size()).isEqualTo(2);
        assertThat(value).isEqualTo("123");

        value = queue.poll().orElse(null);
        assertThat(queue.size()).isEqualTo(1);
        assertThat(value).isEqualTo("456");

        value = queue.poll().orElse(null);
        assertThat(queue.size()).isZero();
        assertThat(value).isEqualTo("789");

        value = queue.poll().orElse(null);
        assertThat(value).isNull();
    }

    @Test
    void testGetName() {
        assertThat(queue.getName()).isEqualTo("testQueue");
    }

    @Test
    void testGetType() {
        assertThat(queue.getType()).isEqualTo(String.class);
    }

    @Test
    void testContains() {
        assertThat(queue.contains("abc")).isFalse();
        queue.put("abc", "123");
        assertThat(queue.contains("abc")).isTrue();
    }

    @Test
    void testSize() {
        assertThat(queue.size()).isZero();
        queue.put("abc", "123");
        queue.put("def", "456");
        assertThat(queue.size()).isEqualTo(2);
        // adding under an existing key should not change the size
        queue.put("abc", "123");
        queue.put("def", "789");
        assertThat(queue.size()).isEqualTo(2);
    }

    @Test
    void testClear() {
        queue.put("abc", "123");
        queue.put("def", "456");
        assertThat(queue.size()).isEqualTo(2);
        queue.clear();
        assertThat(queue.size()).isZero();
    }

    @Test
    void testForEach() {
        queue.put("abc", "123");
        queue.put("def", "456");
        queue.put("ghi", "789");

        // read all
        List<String> allEntries = new ArrayList<>();
        var allResp = queue.forEach((k, v) -> {
            allEntries.add(k + v);
            return true;
        });
        assertThat(allResp).isTrue();
        assertThat(allEntries).isEqualTo(List.of("abc123", "def456", "ghi789"));

        // read some
        List<String> someEntries = new ArrayList<>();
        var someResp = queue.forEach((k, v) -> {
            someEntries.add(k + v);
            return false;
        });
        assertThat(someResp).isFalse();
        assertThat(someEntries).isEqualTo(List.of("abc123"));
    }

    @Test
    void testIsEmpty() {
        assertThat(queue.isEmpty()).isTrue();
        queue.put("abc", "123");
        queue.put("def", "456");
        assertThat(queue.isEmpty()).isFalse();
    }

}
