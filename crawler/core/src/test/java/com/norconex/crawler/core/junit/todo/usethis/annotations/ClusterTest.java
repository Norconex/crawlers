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
package com.norconex.crawler.core.junit.todo.usethis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.Tag;

/**
 * Marks a test as requiring a cluster setup - typically executes in
 * 10-60 seconds.
 * Cluster tests include:
 * <ul>
 *   <li>Multi-JVM coordination tests</li>
 *   <li>Infinispan/JGroups cluster formation</li>
 *   <li>Coordinator election and failover</li>
 *   <li>Distributed cache synchronization</li>
 * </ul>
 * <p>
 * Cluster tests run on every PR and before merges, but may be
 * skipped for individual commits during development.
 * </p>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Tag("cluster")
public @interface ClusterTest {
}
