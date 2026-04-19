/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

/**
 * Unit tests for {@link TypedJdbcQueueStoreFactory} covering
 * {@code getValueClass}, {@code setValueClass}, and
 * {@code setHazelcastInstance}.
 */
@Timeout(30)
class TypedJdbcQueueStoreFactoryTest {

    @Test
    void getValueClass_whenNullValueClass_returnsNull() {
        var factory = new TypedJdbcQueueStoreFactory();
        assertThat(factory.getValueClass()).isNull();
    }

    @Test
    void getValueClass_withValidClass_returnsClass() {
        var factory = new TypedJdbcQueueStoreFactory();
        factory.setValueClass(StepRecord.class);
        assertThat(factory.getValueClass()).isEqualTo(StepRecord.class);
    }

    @Test
    void getValueClass_withStringClass_returnsStringClass() {
        var factory = new TypedJdbcQueueStoreFactory();
        factory.setValueClass(String.class);
        assertThat(factory.getValueClass()).isEqualTo(String.class);
    }

    @Test
    void setValueClass_withNull_clearsClassName() {
        var factory = new TypedJdbcQueueStoreFactory();
        factory.setValueClass(StepRecord.class);
        factory.setValueClass(null);
        assertThat(factory.getValueClass()).isNull();
    }

    @Test
    void setHazelcastInstance_storesInstanceAndName() {
        var hz = mock(HazelcastInstance.class);
        when(hz.getName()).thenReturn("test-hz-queue-instance");

        var factory = new TypedJdbcQueueStoreFactory();
        factory.setHazelcastInstance(hz);

        assertThat(factory.getHazelcastInstance()).isSameAs(hz);
    }

    @Test
    void setHazelcastInstance_withNull_clearsInstance() {
        var hz = mock(HazelcastInstance.class);
        when(hz.getName()).thenReturn("queue-inst");

        var factory = new TypedJdbcQueueStoreFactory();
        factory.setHazelcastInstance(hz);
        factory.setHazelcastInstance(null);

        assertThat(factory.getHazelcastInstance()).isNull();
    }
}
