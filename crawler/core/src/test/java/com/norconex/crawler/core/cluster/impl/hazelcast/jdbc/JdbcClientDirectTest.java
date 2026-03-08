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
package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;

import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;
import com.norconex.crawler.core.cluster.ClusterException;
import com.norconex.crawler.core.cluster.impl.hazelcast.HazelcastConfigurerContext;
import com.norconex.crawler.core.cluster.impl.hazelcast.JdbcHazelcastConfigurer;
import com.norconex.crawler.core.junit.annotations.SlowTest;

/**
 * Direct unit tests for {@link JdbcClient} covering
 * {@code getConnection()}, {@code tableExists()}, {@code ensureTableExists()},
 * and {@code executeInTransaction()}.
 *
 * <p>Uses the standalone Hazelcast/H2 configuration (no containers).</p>
 */
@Timeout(60)
@SlowTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JdbcClientDirectTest {

    private Path tempDir;
    private HazelcastInstance hz;
    private JdbcClient client;

    @BeforeAll
    void setUp() throws IOException {
        tempDir = Files.createTempDirectory("hz-jdbc-direct-test-");
        hz = newInstance();
        var props = new Properties();
        props.setProperty(JdbcClient.PROP_DATA_CONN_REF, "jdbc-datasource");
        client = new JdbcClient(hz, props);
    }

    @AfterAll
    void tearDown() {
        Hazelcast.shutdownAll();
    }

    // -----------------------------------------------------------------------
    // getConnection()
    // -----------------------------------------------------------------------

    @Test
    void getConnection_returnsOpenConnection() throws Exception {
        try (var conn = client.getConnection()) {
            assertThat(conn).isNotNull();
            assertThat(conn.isClosed()).isFalse();
        }
    }

    // -----------------------------------------------------------------------
    // tableExists() / ensureTableExists()
    // -----------------------------------------------------------------------

    @Test
    void tableExists_returnsFalse_forMissingTable() throws Exception {
        assertThat(client.tableExists(
                "test_missing_" + System.nanoTime())).isFalse();
    }

    @Test
    void ensureTableExists_createsTableSuccessfully() throws Exception {
        var tableName = "test_create_" + System.nanoTime();
        assertThat(client.tableExists(tableName)).isFalse();

        client.ensureTableExists(tableName,
                List.of("k VARCHAR(256) PRIMARY KEY", "v TEXT"));

        assertThat(client.tableExists(tableName)).isTrue();
    }

    @Test
    void ensureTableExists_isIdempotent_secondCallDoesNotThrow()
            throws Exception {
        var tableName = "test_idem_" + System.nanoTime();

        // First call creates the table
        client.ensureTableExists(tableName,
                List.of("k VARCHAR(256) PRIMARY KEY", "v TEXT"));
        // Second call should be a no-op (no exception)
        client.ensureTableExists(tableName,
                List.of("k VARCHAR(256) PRIMARY KEY", "v TEXT"));

        assertThat(client.tableExists(tableName)).isTrue();
    }

    // -----------------------------------------------------------------------
    // executeInTransaction()
    // -----------------------------------------------------------------------

    /**
     * A successful transaction inserts a row that is readable afterward.
     */
    @Test
    void executeInTransaction_successfulInsert_rowIsVisible() throws Exception {
        var tableName = "test_tx_ok_" + System.nanoTime();
        client.ensureTableExists(tableName,
                List.of("k VARCHAR(256) PRIMARY KEY", "v TEXT"));

        client.executeInTransaction(conn -> {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO \"" + tableName + "\" (k, v) VALUES (?, ?)")) {
                ps.setString(1, "mykey");
                ps.setString(2, "myvalue");
                ps.executeUpdate();
            }
        });

        // Verify the row is present outside the transaction
        try (var conn = client.getConnection();
                var ps = conn.prepareStatement(
                        "SELECT v FROM \"" + tableName
                                + "\" WHERE k = ?")) {
            ps.setString(1, "mykey");
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).isEqualTo("myvalue");
            }
        }
    }

    /**
     * A transaction that throws a {@link java.sql.SQLException} must be rolled
     * back and a {@link ClusterException} must be thrown to the caller.
     */
    @Test
    void executeInTransaction_sqlExceptionCausesRollback_andThrowsClusterEx()
            throws Exception {
        var tableName = "test_tx_fail_" + System.nanoTime();
        client.ensureTableExists(tableName,
                List.of("k VARCHAR(256) PRIMARY KEY", "v TEXT"));

        // First insert succeeds so there is something to look at after rollback
        client.executeInTransaction(conn -> {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO \"" + tableName + "\" (k, v) VALUES (?, ?)")) {
                ps.setString(1, "beforeFail");
                ps.setString(2, "x");
                ps.executeUpdate();
            }
        });

        // Second transaction: insert a valid row first, then cause a failure
        assertThatThrownBy(() -> client.executeInTransaction(conn -> {
            // Insert duplicate key – violates PRIMARY KEY → triggers rollback
            try (var ps = conn.prepareStatement(
                    "INSERT INTO \"" + tableName + "\" (k, v) VALUES (?, ?)")) {
                ps.setString(1, "beforeFail"); // duplicate!
                ps.setString(2, "y");
                ps.executeUpdate();
            }
        })).isInstanceOf(ClusterException.class);

        // The original row must still be intact (rollback occurred)
        try (var conn = client.getConnection();
                var ps = conn.prepareStatement(
                        "SELECT count(*) FROM \"" + tableName + "\"")) {
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertThat(rs.getInt(1)).isEqualTo(1);
            }
        }
    }

    /**
     * Multiple rows inserted in a single transaction are all committed atomically.
     */
    @Test
    void executeInTransaction_multipleInsertsCommittedAtomically()
            throws Exception {
        var tableName = "test_tx_multi_" + System.nanoTime();
        client.ensureTableExists(tableName,
                List.of("k VARCHAR(256) PRIMARY KEY", "v TEXT"));

        client.executeInTransaction(conn -> {
            try (var ps = conn.prepareStatement(
                    "INSERT INTO \"" + tableName + "\" (k, v) VALUES (?, ?)")) {
                for (int i = 0; i < 5; i++) {
                    ps.setString(1, "k" + i);
                    ps.setString(2, "v" + i);
                    ps.addBatch();
                }
                ps.executeBatch();
            }
        });

        try (var conn = client.getConnection();
                var ps = conn.prepareStatement(
                        "SELECT count(*) FROM \"" + tableName + "\"");
                var rs = ps.executeQuery()) {
            rs.next();
            assertThat(rs.getInt(1)).isEqualTo(5);
        }
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private HazelcastInstance newInstance() {
        var ctx =
                new HazelcastConfigurerContext(tempDir, false, "test-cluster");
        return Hazelcast.newHazelcastInstance(
                new JdbcHazelcastConfigurer().buildConfig(ctx));
    }
}
