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
package com.norconex.grid.jdbc;

import static org.assertj.core.api.Assertions.fail;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.jgroups.protocols.BARRIER;
import org.jgroups.protocols.FD_ALL;
import org.jgroups.protocols.FD_SOCK;
import org.jgroups.protocols.FRAG2;
import org.jgroups.protocols.MERGE3;
import org.jgroups.protocols.MFC;
import org.jgroups.protocols.PING;
import org.jgroups.protocols.UDP;
import org.jgroups.protocols.UFC;
import org.jgroups.protocols.UNICAST3;
import org.jgroups.protocols.VERIFY_SUSPECT;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridTestSuite;
import com.norconex.grid.core.util.NetUtil;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlGridTestSuite extends GridTestSuite {

    //    private static GossipRouter gossipRouter;
    //    private static AtomicInteger gossipPort = new AtomicInteger();
    private AtomicInteger nodeIndex = new AtomicInteger();
    private AtomicInteger multiCastPort = new AtomicInteger();

    @SuppressWarnings("resource")
    @Container
    private static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15.12-alpine")
                    .withUsername("test")
                    .withPassword("test");

    private static final AtomicInteger DB_COUNT = new AtomicInteger();
    private JdbcGridConnector gridConnector;
    private String uniqueDbName;
    //    private final List<IpAddress> initialHosts = new CopyOnWriteArrayList<>();

    @BeforeEach
    void beforeEachPostgreSQL() throws Exception {
        multiCastPort.set(NetUtil.getAvailablePort());
        //        gossipPort.set(NetUtil.getAvailablePort());
        //        gossipRouter = new GossipRouter(
        //                NetUtil.localInetAddress(), gossipPort.get());
        //        gossipRouter.start();
        //
        nodeIndex.set(0);
        //        initialHosts.clear();
        uniqueDbName = "mock_db_" + DB_COUNT.incrementAndGet();
        try (var conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE " + uniqueDbName);
        }

        gridConnector = new JdbcGridConnector();
        var config = gridConnector.getConfiguration();
        var ds = config.getDatasource();
        ds.add("jdbcUrl",
                postgres.getJdbcUrl().replace("/test", "/" + uniqueDbName));
        ds.add("username", postgres.getUsername());
        ds.add("password", postgres.getPassword());
    }

    @AfterEach
    void afterEachPostgreSQL() throws SQLException {
        //        initialHosts.clear();
        //        nodeIndex.set(0);

        try (var conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                var stmt = conn.createStatement()) {
            stmt.execute("DROP DATABASE IF EXISTS " + uniqueDbName
                    + " WITH (FORCE)");
        }
        //
        //        if (gossipRouter != null) {
        //            gossipRouter.stop();
        //        }
    }

    @Override
    protected void assertCleanup() throws Exception {
        LOG.info("Ensuring proper PostgreSQL test cleanup.");
        try (var conn = DriverManager.getConnection(
                postgres.getJdbcUrl(),
                postgres.getUsername(),
                postgres.getPassword());
                var stmt = conn.createStatement()) {
            // ensure everything is clean
            var sql = """
                SELECT pid, usename, state, query, backend_start
                FROM pg_stat_activity
                WHERE datname = '%s'
                  AND state != 'idle'
                  AND query NOT ILIKE '%%pg_stat_activity%%'
            """.formatted(uniqueDbName);

            try (var rs = stmt.executeQuery(sql)) {
                List<String> connections = new ArrayList<>();
                while (rs.next()) {
                    connections.add(
                            "pid=%s, user=%s, state=%s, query=%s, started=%s"
                                    .formatted(
                                            rs.getString(1),
                                            rs.getString(2),
                                            rs.getString(3),
                                            rs.getString(4),
                                            rs.getString(5)));
                }
                if (!connections.isEmpty()) {
                    LOG.error("Active connections detected after test:\n{}",
                            String.join("\n", connections));
                    fail("Test left connections open: " + connections.size());
                }
            }

        }
        LOG.info("PostgreSQL resources seem clean.");
    }

    @Override
    protected GridConnector getGridConnector(String gridName) {
        LOG.info("Connecting to a grid backed by PosgreSQL: {}",
                postgres.getJdbcUrl());
        gridConnector.getConfiguration()
                .setGridName(gridName)
                .setProtocols(createProtocolList());
        return gridConnector;
    }

    //        var nodeIndex = initialHosts.size();
    //        var nodePort = NetUtil.getAvailablePort();
    //        initialHosts.add(NetUtil.localIpAddress(nodePort));

    //        LOG.info("Starting node on port {} using TCPGOSSIP to {}",
    //                nodePort, gossipPort.get());

    private synchronized List<Protocol> createProtocolList() {
        System.setProperty("jgroups.debug", "true");
        return List.of(
                new UDP()
                        //                        .setValue("mcast_addr", InetAddress.getByName("228.8.8.8"))
                        .setValue("mcast_port", multiCastPort.get())
                        // let the OS assign a free unicast port for your socket
                        .setValue("bind_port", 0),
                new PING(),
                new MERGE3(),
                new FD_SOCK(),
                new FD_ALL(),
                new VERIFY_SUSPECT(),
                new BARRIER(),
                new NAKACK2(),
                new UNICAST3(),
                new STABLE(),
                new GMS(),
                new UFC(),
                new MFC(),
                new FRAG2());
    }
}

//var baseId =
//new AtomicInteger(1000 + nodeIndex.getAndIncrement() * 100);
//protocols.forEach(prot -> prot.setId((short) baseId.incrementAndGet()));

//
//                new TCP()
//                        .setValue("bind_addr", NetUtil.localInetAddress())
//                        .setValue("bind_port", nodePort)
//                        .setValue("external_addr", NetUtil.localInetAddress()),
//                //                        .setValue("singleton_name", "mock-node-" + nodeIndex),
//                new TCPGOSSIP()
//                        .setValue("initial_hosts", List.of(
//                                NetUtil.localInetSocketAddress(
//                                        gossipPort.get()))),
//                new MERGE3(),
//                new FD_SOCK()
//                        .setValue("start_port", NetUtil.getAvailablePort()),
//                new FD_ALL(),
//                new VERIFY_SUSPECT(),
//                new BARRIER(),
//                new NAKACK2(),
//                new UNICAST3(),
//                new STABLE(),
//                new GMS(),
//                new UFC(),
//                new MFC(),
//                new FRAG2());
