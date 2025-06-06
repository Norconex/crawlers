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

public abstract class GridQueueTest {

    @SingleNodeTest
    void testPutPoll(GridQueue<String> queue) {
        assertThat(queue.isEmpty()).isTrue();
        assertThat(queue.size()).isZero();

        assertThat(queue.put("abc", "123")).isTrue();
        // should not add if already exists with same key
        assertThat(queue.put("abc", "321")).isFalse();
        assertThat(queue.put("def", "456")).isTrue();
        assertThat(queue.put("ghi", "789")).isTrue();

        assertThat(queue.isEmpty()).isFalse();
        assertThat(queue.size()).isEqualTo(3);

        var value = queue.poll().orElse(null);

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

    @SingleNodeTest
    void testGetName(GridQueue<String> queue) {
        assertThat(queue.getName())
                .startsWith(ClusterExtension.QUEUE_STORE_PREFIX);
    }

    @SingleNodeTest
    void testGetType(GridQueue<String> queue) {
        assertThat(queue.getType()).isEqualTo(String.class);
    }

    @SingleNodeTest
    void testContains(GridQueue<String> queue) {
        assertThat(queue.contains("abc")).isFalse();
        queue.put("abc", "123");
        assertThat(queue.contains("abc")).isTrue();
    }

    @SingleNodeTest
    void testSize(GridQueue<String> queue) {
        assertThat(queue.size()).isZero();
        queue.put("abc", "123");
        queue.put("def", "456");
        assertThat(queue.size()).isEqualTo(2);
        // adding under an existing key should not change the size
        queue.put("abc", "123");
        queue.put("def", "789");
        assertThat(queue.size()).isEqualTo(2);
    }

    @SingleNodeTest
    void testClear(GridQueue<String> queue) {
        queue.put("abc", "123");
        queue.put("def", "456");
        assertThat(queue.size()).isEqualTo(2);
        queue.clear();
        assertThat(queue.size()).isZero();
    }

    @SingleNodeTest
    void testForEach(GridQueue<String> queue) {
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

    @SingleNodeTest
    void testIsEmpty(GridQueue<String> queue) {
        assertThat(queue.isEmpty()).isTrue();
        queue.put("abc", "123");
        queue.put("def", "456");
        assertThat(queue.isEmpty()).isFalse();
    }

}
