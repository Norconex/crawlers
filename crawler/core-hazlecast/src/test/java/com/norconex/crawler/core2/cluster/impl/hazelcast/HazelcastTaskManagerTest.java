/*
 * Copyright 2014-2025 Norconex Inc.
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
package com.norconex.crawler.core2.cluster.impl.hazelcast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.Serializable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.config.Config;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core2.cluster.ClusterTask;
import com.norconex.crawler.core2.cluster.TaskException;

class HazelcastTaskManagerTest {

    private HazelcastInstance hazelcastInstance;
    private HazelcastTaskManager taskManager;

    @BeforeEach
    void setUp() {
        // Create an isolated test instance of Hazelcast
        Config config = new Config();
        config.setClusterName("test-task-cluster-" + System.currentTimeMillis());
        config.getNetworkConfig().getJoin().getMulticastConfig().setEnabled(false);
        config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(false);
        hazelcastInstance = Hazelcast.newHazelcastInstance(config);
        
        // Create the task manager
        taskManager = new HazelcastTaskManager(hazelcastInstance);
    }

    @AfterEach
    void tearDown() {
        if (taskManager != null) {
            taskManager.shutdown();
        }
        if (hazelcastInstance != null) {
            hazelcastInstance.shutdown();
        }
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testSuccessfulTask() throws Exception {
        // Given
        SuccessfulTask task = new SuccessfulTask("Hello, Hazelcast!");
        
        // When
        CompletableFuture<String> future = taskManager.submitTask(task);
        String result = future.get(5, TimeUnit.SECONDS);
        
        // Then
        assertEquals("Hello, Hazelcast!", result);
        assertTrue(taskManager.getTaskStatuses().size() > 0);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    void testFailingTask() {
        // Given
        FailingTask task = new FailingTask("Expected failure");
        
        // When
        CompletableFuture<String> future = taskManager.submitTask(task);
        
        // Then
        ExecutionException exception = assertThrows(ExecutionException.class, 
                () -> future.get(5, TimeUnit.SECONDS));
        assertTrue(exception.getCause() instanceof TaskException);
        assertTrue(exception.getCause().getMessage().contains("Expected failure"));
        assertTrue(taskManager.getTaskStatuses().size() > 0);
    }

    // Simple successful task implementation
    private static class SuccessfulTask implements ClusterTask<String>, Serializable {
        private static final long serialVersionUID = 1L;
        private final String message;
        
        public SuccessfulTask(String message) {
            this.message = message;
        }
        
        @Override
        public String call() {
            return message;
        }
    }
    
    // Simple failing task implementation
    private static class FailingTask implements ClusterTask<String>, Serializable {
        private static final long serialVersionUID = 1L;
        private final String errorMessage;
        
        public FailingTask(String errorMessage) {
            this.errorMessage = errorMessage;
        }
        
        @Override
        public String call() {
            throw new TaskException(errorMessage);
        }
    }
}