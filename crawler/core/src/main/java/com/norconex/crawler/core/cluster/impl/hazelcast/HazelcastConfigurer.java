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

import com.hazelcast.config.Config;

/**
 * Strategy interface for building a Hazelcast {@link Config} programmatically.
 * <p>
 * The default implementation is {@link JdbcHazelcastConfigurer}, which
 * configures Hazelcast with a JDBC data source for persistence. In standalone
 * mode an embedded H2 database is used automatically; in clustered mode an
 * external JDBC URL must be supplied.
 * </p>
 * <p>
 * Advanced users can implement this interface to supply a fully custom
 * Hazelcast configuration. Those who prefer file-based configuration can
 * delegate to
 * {@link HazelcastConfigLoader#load(String, java.util.Map)} inside their
 * implementation to base it on a YAML or XML descriptor.
 * </p>
 *
 * @see JdbcHazelcastConfigurer
 * @see HazelcastConfigLoader
 */
@FunctionalInterface
public interface HazelcastConfigurer {

    /**
     * Builds a Hazelcast {@link Config} for the given context.
     * The returned config must <em>not</em> yet have a
     * {@code HazelcastInstance} started against it.
     *
     * @param ctx context carrying work directory, clustering mode, and cluster
     *            name
     * @return a fully-configured (but not yet started) Hazelcast {@link Config}
     */
    Config buildConfig(HazelcastConfigurerContext ctx);
}
