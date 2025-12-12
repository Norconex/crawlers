package com.norconex.crawler.core.cluster.impl.hazelcast;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.sql.DataSource;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.map.MapStore;

import lombok.extern.slf4j.Slf4j;

/**
 * Simple JDBC-based MapStore for Hazelcast OSS.
 * This is a workaround since GenericMapStore with external-data-stores
 * is an Enterprise-only feature in Hazelcast 5.6.
 */
@Slf4j
public class SimpleJdbcMapStore
        implements MapStore<String, String>,
        MapLoaderLifecycleSupport {

    private DataSource dataSource;
    private String jdbcUrl;
    private String username;
    private String password;
    private String tableName;
    private String keyColumn;
    private String valueColumn;

    /**
     * No-argument constructor required by Hazelcast.
     * Properties will be injected via init() method.
     */
    public SimpleJdbcMapStore() {
        // Properties will be set in init()
    }

    public SimpleJdbcMapStore(
            DataSource dataSource,
            String tableName,
            String keyColumn,
            String valueColumn) {
        this.dataSource = dataSource;
        this.tableName = tableName;
        this.keyColumn = keyColumn;
        this.valueColumn = valueColumn;
    }

    @Override
    public void init(
            HazelcastInstance hazelcastInstance,
            Properties properties,
            String mapName) {
        // If already initialized via constructor, skip
        if (dataSource != null) {
            return;
        }

        jdbcUrl = properties.getProperty("jdbcUrl");
        username = properties.getProperty("username", "sa");
        password = properties.getProperty("password", "");
        tableName = properties.getProperty("tableName", "CRAWLER");
        keyColumn = properties.getProperty("keyColumn", "map_key");
        valueColumn = properties.getProperty("valueColumn", "map_value");

        LOG.info(
                "Initializing SimpleJdbcMapStore for map '{}' "
                        + "with JDBC URL: {}",
                mapName,
                jdbcUrl);
    }

    @Override
    public void destroy() {
        // No cleanup needed for DriverManager-based connections
        LOG.info("SimpleJdbcMapStore destroyed");
    }

    /**
     * Get a database connection.
     * Uses DataSource if available, otherwise DriverManager.
     */
    private Connection getConnection() throws SQLException {
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    @Override
    public void store(String key, String value) {
        var sql = String.format(
                "MERGE INTO %s (%s, %s) KEY (%s) VALUES (?, ?)",
                tableName, keyColumn, valueColumn, keyColumn);

        try (var conn = getConnection();
                var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to store key: " + key, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void storeAll(Map<String, String> map) {
        for (Map.Entry<String, String> entry : map.entrySet()) {
            store(entry.getKey(), entry.getValue());
        }
    }

    @Override
    public void delete(String key) {
        var sql = String.format(
                "DELETE FROM %s WHERE %s = ?",
                tableName, keyColumn);

        try (var conn = getConnection();
                var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            stmt.executeUpdate();
        } catch (SQLException e) {
            LOG.error("Failed to delete key: " + key, e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deleteAll(Collection<String> keys) {
        for (String key : keys) {
            delete(key);
        }
    }

    @Override
    public String load(String key) {
        var sql = String.format(
                "SELECT %s FROM %s WHERE %s = ?",
                valueColumn, tableName, keyColumn);

        try (var conn = getConnection();
                var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, key);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (SQLException e) {
            LOG.error("Failed to load key: " + key, e);
            throw new RuntimeException(e);
        }
        return null;
    }

    @Override
    public Map<String, String> loadAll(Collection<String> keys) {
        Map<String, String> result = new HashMap<>();
        for (String key : keys) {
            var value = load(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    @Override
    public Iterable<String> loadAllKeys() {
        var sql = String.format("SELECT %s FROM %s", keyColumn, tableName);
        List<String> keys = new ArrayList<>();

        try (var conn = getConnection();
                var stmt = conn.prepareStatement(sql);
                var rs = stmt.executeQuery()) {
            while (rs.next()) {
                keys.add(rs.getString(1));
            }
        } catch (SQLException e) {
            LOG.error("Failed to load all keys", e);
            throw new RuntimeException(e);
        }
        return keys;
    }
}
