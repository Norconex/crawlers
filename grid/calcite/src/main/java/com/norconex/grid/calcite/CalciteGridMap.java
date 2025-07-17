package com.norconex.grid.calcite;

import static com.norconex.grid.core.util.SerialUtil.fromJson;

import java.util.Objects;
import java.util.function.BiPredicate;

import com.norconex.grid.calcite.db.Db;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.util.SerializableUnaryOperator;

public class CalciteGridMap<T>
        extends BaseCalciteGridStore<T> implements GridMap<T> {

    public CalciteGridMap(
            Db dbHelper, String name, Class<? extends T> type) {
        super(dbHelper, name, type);
        dbHelper.createTableIfNotExists(dbHelper.isRowTenancy()
                ? """
                    CREATE TABLE %s (
                        id VARCHAR(%s) NOT NULL,
                        json CLOB,
                        namespace VARCHAR(%s)
                        PRIMARY KEY (id, namespace)
                    );
                    """.formatted(getTableName(),
                        Db.ID_MAX_LENGTH,
                        Db.NS_MAX_LENGTH)
                : """
                    CREATE TABLE %s (
                        id VARCHAR(%s) NOT NULL,
                        json CLOB,
                        PRIMARY KEY (id)
                    );
                    """.formatted(
                        getTableName(),
                        Db.ID_MAX_LENGTH),
                getTableName());
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        return getDbHelper().executeRead((getDbHelper().isRowTenancy()
                ? "SELECT id, json FROM %s"
                : "SELECT id, json FROM %s WHERE namespace = ?")
                        .formatted(getTableName()),
                ps -> {
                    if (getDbHelper().isRowTenancy()) {
                        ps.setString(1, getDbHelper().getNamespace());
                    }
                },
                rs -> {
                    while (rs.next()) {
                        if (!predicate.test(
                                rs.getString(1),
                                fromJson(rs.getString(2), getType()))) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    @Override
    public boolean put(String key, T object) {
        return getDbHelper().upsert(getTableName(), key, object);
    }

    @Override
    public boolean update(String key, SerializableUnaryOperator<T> updater) {
        return getDbHelper().runInTransactionAndReturn(conn -> {
            // Insert dummy row first so we can lock it.
            // This is required to avoid concurrency issues by some DBs.
            getDbHelper().insertIfAbsent(getTableName(), key, "LOCK_HACK");

            // Lock the row
            T existingValue = null;
            try (var lockStmt = conn.prepareStatement(
                    (getDbHelper().isRowTenancy()
                            ? "SELECT json FROM %s WHERE id = ? "
                                    + "AND namespace = ? FOR UPDATE;"
                            : "SELECT json FROM %s WHERE id = ? FOR UPDATE;")
                                    .formatted(getTableName()))) {
                lockStmt.setString(1, Db.rightSizeId(key));
                if (getDbHelper().isRowTenancy()) {
                    lockStmt.setString(2, getDbHelper().getNamespace());
                }
                try (var rs = lockStmt.executeQuery()) {
                    if (rs.next()) {
                        var json = rs.getString("json");
                        if (!"\"LOCK_HACK\"".equals(json)) {
                            existingValue = fromJson(json, getType());
                        }
                    }
                }
            }

            var newValue = updater.apply(existingValue);
            put(key, newValue);
            return !Objects.equals(existingValue, newValue);
        });
    }

    @Override
    public T get(String key) {
        return getDbHelper().getById(getTableName(), key, getType());
    }

    @Override
    public boolean delete(String key) {
        return getDbHelper().deleteById(getTableName(), key);
    }
}
