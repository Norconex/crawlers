package com.norconex.crawler.core.cluster.impl.hazelcast.jdbc;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.map.MapLoaderLifecycleSupport;
import com.hazelcast.map.MapStore;
import com.norconex.crawler.core.cluster.ClusterException;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * JDBC-based key-value MapStore for Hazelcast OSS. Used in Hazelcast
 * configuration object/files. Both keys and values are strings.
 */
@Slf4j
public class JdbcMapStore
        implements MapStore<String, String>, MapLoaderLifecycleSupport {

    private static class Sqls {
        private String store;
        private String delete;
        private String load;
        private String loadAllKeys;

        Sqls(String tableName, String mergeSql) {
            //--- Store ---
            store = (StringUtils.isNotBlank(mergeSql)
                    ? mergeSql
                    : """
                        MERGE INTO "${tableName}" AS T
                        USING (SELECT ? AS k, ? AS v) AS S
                        ON (T.k = S.k)
                        WHEN MATCHED THEN
                            UPDATE SET T.v = S.v
                        WHEN NOT MATCHED THEN
                            INSERT (k, v)
                            VALUES (S.k, S.v);
                        """)
                            .replace("{tableName}", tableName);

            //--- Delete ---
            delete = "DELETE FROM \"%s\" WHERE k = ?".formatted(tableName);

            //--- Load ---
            load = "SELECT v FROM \"%s\" WHERE k = ?".formatted(tableName);

            //--- Load all keys ---
            loadAllKeys = "SELECT k FROM \"%s\"".formatted(tableName);
        }
    }

    private DbClient db;
    private Sqls sqls;
    private String tableName;

    @Override
    public final void init(
            HazelcastInstance hzInstance,
            Properties storeProps,
            String storeName) {

        db = new DbClient(hzInstance, storeProps);
        tableName = storeProps.getProperty(DbClient.PROP_TABLE_NAME, storeName);
        sqls = new Sqls(tableName, storeProps.getProperty("sql-merge"));

        LOG.info("Initializing Jdbc map store '{}' with table '{}'.",
                storeName, tableName);
        try {
            db.ensureTableExists(tableName,
                    "k " + storeProps.getProperty(
                            "column-key-type", "VARCHAR(4096)"),
                    "v " + storeProps.getProperty("column-value-type", "CLOB"));
        } catch (SQLException e) {
            throw new ClusterException("Can't initialize JDBC map store.", e);
        }
    }

    @Override
    public void destroy() {
        //NOOP No cleanup needed with JDBC store
    }

    @Override
    public void store(@NonNull String key, @NonNull String value) {
        try (var conn = db.getConnection();
                var stmt = conn.prepareStatement(sqls.store)) {
            stmt.setString(1, key);
            stmt.setString(2, value);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new ClusterException(
                    "Failed to store record (key=%s)".formatted(key), e);
        }
    }

    @Override
    public void storeAll(Map<String, String> map) {
        try (var conn = db.getConnection();
                var stmt = conn.prepareStatement(sqls.store)) {
            for (var entry : map.entrySet()) {
                stmt.setString(1, entry.getKey());
                stmt.setString(2, entry.getValue());
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            throw new ClusterException("Failed to store all records", e);
        }
    }

    @Override
    public void delete(String key) {
        try (var conn = db.getConnection();
                var stmt = conn.prepareStatement(sqls.delete)) {
            stmt.setString(1, key);
            stmt.executeUpdate();
        } catch (Exception e) {
            throw new ClusterException("Failed to delete record (key=%s)"
                    .formatted(key), e);
        }
    }

    @Override
    public void deleteAll(Collection<String> keys) {
        for (var key : keys) {
            delete(key);
        }
    }

    @Override
    public String load(String key) {
        try (var conn = db.getConnection();
                var stmt = conn.prepareStatement(sqls.load)) {
            stmt.setString(1, key);
            try (var rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString(1);
                }
            }
        } catch (Exception e) {
            throw new ClusterException("Failed to load record (key=%s)"
                    .formatted(key), e);
        }
        return null;
    }

    @Override
    public Map<String, String> loadAll(Collection<String> keys) {
        Map<String, String> result = new HashMap<>();
        if (keys == null || keys.isEmpty()) {
            return result;
        }
        var sql = "SELECT k, v FROM \"%s\" WHERE k IN (%s)".formatted(
                tableName,
                String.join(",", Collections.nCopies(keys.size(), "?")));
        try (var conn = db.getConnection();
                var stmt = conn.prepareStatement(sql)) {
            var i = 1;
            for (String key : keys) {
                stmt.setString(i++, key);
            }
            try (var rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.put(rs.getString(1), rs.getString(2));
                }
            }
        } catch (SQLException e) {
            throw new ClusterException("Failed to load all records", e);
        }
        return result;
    }

    @Override
    public Iterable<String> loadAllKeys() {
        List<String> keys = new ArrayList<>();
        try (var conn = db.getConnection();
                var stmt = conn.prepareStatement(sqls.loadAllKeys);
                var rs = stmt.executeQuery()) {
            while (rs.next()) {
                keys.add(rs.getString(1));
            }
        } catch (Exception e) {
            throw new ClusterException("Failed to load all keys.", e);
        }
        return keys;
    }
}
