package com.norconex.crawler.core.junit.cluster.state;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import org.h2.tools.Server;

import lombok.SneakyThrows;

/**
 * A lightweight state-coordination database server for multi-nodes cluster
 * testing.
 */
public class StateDbServer {
    private static String jdbcUrl;
    private Server h2Server;
    private int port;
    private final Path workDir;
    private final String dbName;
    //    private Connection conn;

    public StateDbServer(Path workDir, String dbName) {
        this.workDir = workDir;
        this.dbName = dbName;
    }

    @SneakyThrows
    public void start() {
        try (var socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Files.createDirectories(workDir);
        var dbPath = workDir.resolve(dbName);
        h2Server = Server.createTcpServer(
                "-tcpPort",
                String.valueOf(port),
                "-ifNotExists")
                .start();
        jdbcUrl = "jdbc:h2:tcp://localhost:" + port + "/" + dbPath
                + ";DB_CLOSE_DELAY=-1";
        //        conn = DriverManager.getConnection(jdbcUrl, "sa", "");
        createTableIfNeeded();
    }

    public void stop() {
        //        try {
        //            if (conn != null)
        //                conn.close();
        //        } catch (Exception ignored) {
        //            // NOOP
        //        }
        if (h2Server != null) {
            h2Server.stop();
        }
    }

    public static String getJdbcUrl() {
        return jdbcUrl;
    }

    public int getPort() {
        return port;
    }

    public String getDbName() {
        return dbName;
    }

    private void createTableIfNeeded() throws SQLException {
        try (var stmt = StateDbClient.get().newConnection().createStatement()) {

            //TODo add "now" timestamp
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cluster_state (
                    id IDENTITY,
                    node VARCHAR(128),
                    topic VARCHAR(128),
                    k VARCHAR(256),
                    v VARCHAR(%s),
                    dt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )""".formatted(StateDbClient.MAX_VALUE_LENGTH));
        }
    }

    //        public void put(String nodeName, String subject, String key, String value)
    //                throws SQLException {
    //            var counter = nextCounter(nodeName, subject, key);
    //            try (var ps = conn.prepareStatement(
    //                    "INSERT INTO cluster_state (node_name, subject, prop, val, counter) VALUES (?, ?, ?, ?, ?)")) {
    //                ps.setString(1, nodeName);
    //                ps.setString(2, subject);
    //                ps.setString(3, key);
    //                ps.setString(4, value);
    //                ps.setInt(5, counter);
    //                ps.executeUpdate();
    //            }
    //        }
    //
    //        public Optional<String> getLatest(String nodeName, String subject,
    //                String key) throws SQLException {
    //            try (var ps = conn.prepareStatement(
    //                    "SELECT val FROM cluster_state WHERE node_name=? AND subject=? AND prop=? ORDER BY counter DESC LIMIT 1")) {
    //                ps.setString(1, nodeName);
    //                ps.setString(2, subject);
    //                ps.setString(3, key);
    //                try (var rs = ps.executeQuery()) {
    //                    if (rs.next())
    //                        return Optional.ofNullable(rs.getString(1));
    //                }
    //            }
    //            return Optional.empty();
    //        }
    //
    //    public List<String> getAll(String nodeName, String subject, String key)
    //            throws SQLException {
    //        List<String> values = new ArrayList<>();
    //        try (var ps = conn.prepareStatement(
    //                "SELECT val FROM cluster_state WHERE node_name=? AND subject=? AND prop=? ORDER BY counter ASC")) {
    //            ps.setString(1, nodeName);
    //            ps.setString(2, subject);
    //            ps.setString(3, key);
    //            try (var rs = ps.executeQuery()) {
    //                while (rs.next())
    //                    values.add(rs.getString(1));
    //            }
    //        }
    //        return values;
    //    }
    //
    //    private int nextCounter(String nodeName, String subject, String key)
    //            throws SQLException {
    //        try (var ps = conn.prepareStatement(
    //                "SELECT MAX(counter) FROM cluster_state WHERE node_name=? AND subject=? AND prop=?")) {
    //            ps.setString(1, nodeName);
    //            ps.setString(2, subject);
    //            ps.setString(3, key);
    //            try (var rs = ps.executeQuery()) {
    //                if (rs.next()) {
    //                    var val = rs.getInt(1);
    //                    return rs.wasNull() ? 0 : val + 1;
    //                }
    //            }
    //        }
    //        return 0;
    //    }
    //
    //    public List<Row> queryByNode(String nodeName) throws SQLException {
    //        List<Row> rows = new ArrayList<>();
    //        try (var ps = conn.prepareStatement(
    //                "SELECT subject, prop, val, counter FROM cluster_state WHERE node_name=?")) {
    //            ps.setString(1, nodeName);
    //            try (var rs = ps.executeQuery()) {
    //                while (rs.next()) {
    //                    rows.add(new Row(nodeName, rs.getString(1), rs.getString(2),
    //                            rs.getString(3), rs.getInt(4)));
    //                }
    //            }
    //        }
    //        return rows;
    //    }
    //
    //    public static class Row {
    //        public final String nodeName, subject, key, value;
    //        public final int counter;
    //
    //        public Row(String nodeName, String subject, String key, String value,
    //                int counter) {
    //            this.nodeName = nodeName;
    //            this.subject = subject;
    //            this.key = key;
    //            this.value = value;
    //            this.counter = counter;
    //        }
    //    }
}
