package com.norconex.grid.calcite;

import static java.util.Optional.ofNullable;

import java.util.Optional;
import java.util.function.BiPredicate;

import com.norconex.grid.calcite.db.Db;
import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.util.SerialUtil;

public class CalciteGridQueue<T>
        extends BaseCalciteGridStore<T> implements GridQueue<T> {

    public CalciteGridQueue(
            Db dbHelper, String name, Class<? extends T> type) {
        super(dbHelper, name, type);
        dbHelper.createTableIfNotExists(dbHelper.isRowTenancy()
                ? """
                    CREATE TABLE %s (
                        id VARCHAR(%s) NOT NULL,
                        json CLOB,
                        namespace VARCHAR(%s)
                        created_at BIGINT,
                        PRIMARY KEY (id, namespace)
                    );
                    CREATE INDEX idx_created_at ON %s (created_at);
                    """.formatted(
                        getTableName(),
                        Db.ID_MAX_LENGTH,
                        Db.NS_MAX_LENGTH,
                        getTableName())
                : """
                    CREATE TABLE %s (
                        id VARCHAR(%s) NOT NULL,
                        json CLOB,
                        created_at BIGINT,
                        PRIMARY KEY (id)
                    );
                    CREATE INDEX idx_created_at ON %s (created_at);
                    """.formatted(
                        getTableName(),
                        Db.ID_MAX_LENGTH,
                        getTableName()),
                getTableName());
    }

    @Override
    public boolean forEach(BiPredicate<String, T> predicate) {
        return getDbHelper().executeRead((getDbHelper().isRowTenancy()
                ? "SELECT id, json FROM %s ORDER BY created_at ASC;"
                : "SELECT id, json FROM %s WHERE namespace = ? "
                        + "ORDER BY created_at ASC;").formatted(getTableName()),
                ps -> {
                    if (getDbHelper().isRowTenancy()) {
                        ps.setString(1, getDbHelper().getNamespace());
                    }
                },
                rs -> {
                    while (rs.next()) {
                        if (!predicate.test(
                                rs.getString(1),
                                SerialUtil.fromJson(
                                        rs.getString(2), getType()))) {
                            return false;
                        }
                    }
                    return true;
                });
    }

    @Override
    public boolean put(String key, T object) {
        return getDbHelper().insertIfAbsent(getTableName(), key, object);
    }

    @Override
    public Optional<T> poll() {
        return ofNullable(getDbHelper().poll(getTableName(), getType()));
    }
}
