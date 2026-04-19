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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.nio.file.Path;

/**
 * Context passed to
 * {@link HazelcastConfigurer#buildConfig(HazelcastConfigurerContext)} with all
 * framework-supplied information needed to build the Hazelcast configuration.
 *
 * @param workDir     crawler work directory; used by standalone mode for the
 *                    embedded H2 database path
 * @param clustered   {@code true} when running in multi-node cluster mode,
 *                    {@code false} for standalone (single-process) mode
 * @param clusterName Hazelcast cluster name; nodes sharing the same name
 *                    will discover each other
 */
public record HazelcastConfigurerContext(
        Path workDir,
        boolean clustered,
        String clusterName) {
}
