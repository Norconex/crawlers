package com.norconex.crawler.core.junit.cluster.state;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.event.Event;

import lombok.Getter;
import lombok.SneakyThrows;

public class StateDbClient {

    public static final int MAX_VALUE_LENGTH = 1_024_000;

    public static final String PROP_NODE_NAME = "node.name";
    public static final String PROP_JDBC_URL = "jdbc.url";

    public static final String TOPIC_EVENT = "event";
    public static final String TOPIC_CACHE = "cache";
    public static final String TOPIC_STDOUT = "stdout";
    public static final String TOPIC_STDERR = "stderr";

    //    private static volatile boolean initialized = false;
    //    private static StateDbServer clusterState;
    private String nodeName;
    @Getter
    private String jdbcUrl;

    private StateDbClient(String nodeName, String jdbcUrl) {
        this.nodeName = nodeName;
        this.jdbcUrl = jdbcUrl;
    }

    public static StateDbClient get() {
        // Client
        var sysNode = System.getProperty(PROP_NODE_NAME);
        var sysUrl = System.getProperty(PROP_JDBC_URL);
        if (sysNode != null && sysUrl != null) {
            return new StateDbClient(sysNode, sysUrl);
        }

        // Test/Server
        if (StateDbServer.getJdbcUrl() != null) {
            return new StateDbClient("unittest", StateDbServer.getJdbcUrl());
        }

        throw new IllegalStateException(
                "State DB server not initialized or no system properties set.");
    }

    public static boolean isInitialized() {
        return (System.getProperty(PROP_NODE_NAME) != null
                && System.getProperty(PROP_JDBC_URL) != null)
                || StateDbServer.getJdbcUrl() != null;
    }

    @SneakyThrows
    public Connection newConnection() {
        return DriverManager.getConnection(jdbcUrl, "sa", "");
    }

    public void saveEvent(Event event) {
        put(TOPIC_EVENT, event.getName(), event.getMessage());
    }

    public List<StateRecord> getEvents() {
        return getRecordsForTopic(TOPIC_EVENT);
    }

    public void printStreamsOrderedByDateTime() {
        doPrintStreamsOrderedBy("dt ASC");
    }

    public void printStreamsOrderedByNode() {
        doPrintStreamsOrderedBy("node ASC, dt ASC");
    }

    @SneakyThrows
    private void doPrintStreamsOrderedBy(String orderBy) {
        try (var ps = newConnection().prepareStatement("""
                SELECT node, topic, k, v
                FROM cluster_state
                WHERE topic IN (?, ?)
                ORDER BY %s
                """.formatted(orderBy))) {
            ps.setString(1, TOPIC_STDOUT);
            ps.setString(2, TOPIC_STDERR);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    String msg = "[%s:%s]%n%s".formatted(
                            rs.getString("node"),
                            rs.getString("k"),
                            rs.getString("v"));
                    if (TOPIC_STDERR.equals(rs.getString("topic"))) {
                        System.err.println("✖️ " + msg);
                    } else {
                        System.out.println("✔️ " + msg);
                    }
                }
            }
        }
    }

    //    public List<StateRecord> getStdoutForAllNodes() {
    //        return getRecordsForTopic(TOPIC_STDOUT);
    //    }
    //
    //    public List<StateRecord> getStdoutForNode(int nodeNumber) {
    //        return getRecordsForTopicAndNode(TOPIC_STDOUT, nodeNumber);
    //    }
    //
    //    public List<StateRecord> getStderrForAllNodes() {
    //        return getRecordsForTopic(TOPIC_STDERR);
    //    }
    //
    //    public List<StateRecord> getStderrForNode(int nodeNumber) {
    //        return getRecordsForTopicAndNode(TOPIC_STDERR, nodeNumber);
    //    }

    @SneakyThrows
    public List<StateRecord> getRecordsForTopic(String topic) {
        var events = new ArrayList<StateRecord>();
        try (var ps = newConnection().prepareStatement(sqlSelectAllFrom()
                + "WHERE topic = ? ORDER BY dt")) {
            ps.setString(1, topic);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(toRecord(rs));
                }
            }
        }
        return events;
    }

    @SneakyThrows
    public List<StateRecord> getRecordsForTopicAndNode(
            String topic, int nodeNumber) {
        var events = new ArrayList<StateRecord>();
        try (var ps = newConnection().prepareStatement(sqlSelectAllFrom()
                + "WHERE topic = ? and node = ? ORDER BY dt")) {
            ps.setString(1, topic);
            ps.setString(2, "node-" + nodeNumber);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    events.add(toRecord(rs));
                }
            }
        }
        return events;
    }

    @SneakyThrows
    public Map<String, Integer> getCountsByNodesForTopicAndKey(
            String topic, String key) {
        var counts = new HashMap<String, Integer>();
        try (var ps = newConnection().prepareStatement("""
                SELECT node, count(*)
                FROM cluster_state
                WHERE topic = ?
                AND k = ?
                GROUP BY node
                """)) {
            ps.setString(1, topic);
            ps.setString(2, key);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return counts;
    }

    @SneakyThrows
    public Map<String, Integer> getCountsByNodesForTopicAndValue(
            String topic, String value) {
        var counts = new HashMap<String, Integer>();
        try (var ps = newConnection().prepareStatement("""
                SELECT node, count(*)
                FROM cluster_state
                WHERE topic = ?
                AND v = ?
                GROUP BY node
                """)) {
            ps.setString(1, topic);
            ps.setString(2, value);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    counts.put(rs.getString(1), rs.getInt(2));
                }
            }
        }
        return counts;
    }

    @SneakyThrows
    public void put(String topic, String key, String value) {
        try (var ps = newConnection().prepareStatement("""
                INSERT INTO cluster_state (node, topic, k, v)
                VALUES (?, ?, ?, ?)
                """)) {
            ps.setString(1, nodeName);
            ps.setString(2, topic);
            ps.setString(3, key);
            ps.setString(4, StringUtils.truncate(
                    value, StateDbClient.MAX_VALUE_LENGTH));
            ps.executeUpdate();
        }
    }

    @SneakyThrows
    public Optional<String> getLatest(
            String nodeName, String topic, String key) {
        try (var ps = newConnection().prepareStatement("""
                SELECT v
                FROM cluster_state
                WHERE node=? AND topic=? AND k=? ORDER BY id DESC LIMIT 1
                """)) {
            ps.setString(1, nodeName);
            ps.setString(2, topic);
            ps.setString(3, key);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.ofNullable(rs.getString(1));
                }
            }
        }
        return Optional.empty();
    }

    private String sqlSelectAllFrom() {
        return "SELECT id, node, topic, k, v, dt FROM cluster_state ";
    }

    private StateRecord toRecord(ResultSet rs) throws SQLException {
        var rec = new StateRecord();
        rec.setId(rs.getLong("id"));
        rec.setNode(rs.getString("node"));
        rec.setTopic(rs.getString("topic"));
        rec.setKey(rs.getString("k"));
        rec.setValue(rs.getString("v"));
        rec.setCreatedAt(rs.getObject("dt", LocalDateTime.class));
        return rec;
    }
}
