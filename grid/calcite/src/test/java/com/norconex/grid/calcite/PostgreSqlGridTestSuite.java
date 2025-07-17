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
package com.norconex.grid.calcite;

import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.norconex.grid.core.AbstractGridTestSuite;
import com.norconex.grid.core.cluster.WithCluster;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Testcontainers(disabledWithoutDocker = true)
@WithCluster(connectorFactory = PostgreSqlClusterTestConnectorFactory.class)
class PostgreSqlGridTestSuite extends AbstractGridTestSuite {

    private static final long serialVersionUID = 1L;

    @SuppressWarnings("resource")
    @Container
    private static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15.12-alpine")
                    .withUsername("test")
                    .withPassword("test");

    @BeforeAll
    static void ensurePostgresIsReady() {
        if (!postgres.isRunning()) {
            // Ensures container is started manually (before tests start)
            postgres.start();
        }
        PostgreSqlClusterTestConnectorFactory.postgres = postgres;
        LOG.info("PostgreSQL container is ready at: {}", postgres.getJdbcUrl());
    }
}
