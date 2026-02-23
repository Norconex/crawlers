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
 * Marks a test as "slow" - typically executes in 1-5+ minutes.
 * Slow tests include (examples):
 * <ul>
 *   <li>Full end-to-end integration tests</li>
 *   <li>Tests requiring external services (databases, web servers)</li>
 *   <li>Large-scale data processing tests</li>
 *   <li>Performance/load tests</li>
 * </ul>
 * <p>
 * Slow tests run nightly or on-demand to avoid blocking development.
 * </p>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Tag("veryslow")
public @interface VerySlowTest {
}
