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
package com.norconex.crawler.core.junit.tomerge_or_delete;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.junit.jupiter.api.extension.ExtendWith;

/**
 * <p>
 * Annotation to enable shared cluster testing where a single cluster
 * is created once before all tests in the class and reused across
 * all test methods. This is more efficient than creating a new cluster
 * for each test method.
 * </p>
 * <p>
 * The cluster startup time is NOT included in individual test timeouts,
 * making test execution more predictable.
 * </p>
 * <p>
 * Usage:
 * </p>
 * <pre>
 * {@literal @}SharedClusteredCrawl (
 *     driverSupplierClass = MyDriver.class,
 *     nodes = 2
 * )
 * class MyClusterTest {
 *
 *     {@literal @}Test
 *     void testSomething(ClusteredCrawlOuput output) {
 *         // Use the cluster output
 *         var result = output.getPipeResult();
 *         // ...
 *     }
 * }
 * </pre>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@ExtendWith(SharedClusteredCrawlExtension.class)
public @interface SharedClusteredCrawl {
    /**
     * The driver supplier class to use for the crawler.
     */
    Class<?> driverSupplierClass();

    /**
     * Number of cluster nodes to create.
     */
    int nodes() default 2;

    /**
     * Number of threads per crawler instance.
     */
    int threads() default 2;

    /**
     * Additional command-line arguments to pass to the crawler.
     */
    String[] extraArgs() default { "start" };
}
