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

    @SneakyThrows
    public List<StateRecord> getEvents() {
        return getRecordsForTopic(TOPIC_EVENT);
    }

    @SneakyThrows
    public List<StateRecord> getStdoutForAllNodes() {
        return getRecordsForTopic(TOPIC_STDOUT);
    }

    @SneakyThrows
    public List<StateRecord> getStdoutForNode(int nodeNumber) {
        return getRecordsForTopicAndNode(TOPIC_STDOUT, nodeNumber);
    }

    @SneakyThrows
    public List<StateRecord> getStderrForAllNodes() {
        return getRecordsForTopic(TOPIC_STDERR);
    }

    @SneakyThrows
    public List<StateRecord> getStderrForNode(int nodeNumber) {
        return getRecordsForTopicAndNode(TOPIC_STDERR, nodeNumber);
    }

    @SneakyThrows
    public List<StateRecord> getRecordsForTopic(String topic) {
        var events = new ArrayList<StateRecord>();
        try (var ps = newConnection().prepareStatement(sqlSelectAllFrom()
                + "WHERE topic = ?")) {
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
                + "WHERE topic = ? and node = ?")) {
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
    public Map<String, Integer> getCountsByNodesForTopicAndValue(
            String topic, String value) {
        var counts = new HashMap<String, Integer>();
        try (var ps = newConnection().prepareStatement("""
                SELECT node, count(*)
                WHERE topic = ?
                AND value = ?
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
            ps.setString(4, StringUtils.truncate(value, 1024));
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
