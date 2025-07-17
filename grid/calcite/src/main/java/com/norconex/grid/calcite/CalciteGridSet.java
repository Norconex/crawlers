package com.norconex.grid.calcite;

import java.util.function.Predicate;

import com.norconex.grid.calcite.db.Db;
import com.norconex.grid.core.storage.GridSet;

public class CalciteGridSet
        extends BaseCalciteGridStore<String> implements GridSet {

    public CalciteGridSet(
            Db dbHelper, String name) {
        super(dbHelper, name, String.class);

        dbHelper.createTableIfNotExists(dbHelper.isRowTenancy()
                ? """
                    CREATE TABLE %s (
                      id VARCHAR(%s) NOT NULL
                      namespace VARCHAR(%s)
                      PRIMARY KEY (id, namespace)
                    );
                    """.formatted(getTableName(),
                        Db.ID_MAX_LENGTH,
                        Db.NS_MAX_LENGTH)
                : """
                    CREATE TABLE %s (
                      id %s,
                      PRIMARY KEY (id)
                    );
                    """.formatted(
                        getTableName(),
                        Db.ID_MAX_LENGTH),
                getTableName());
    }

    @Override
    public boolean add(String id) {
        return getDbHelper().insertIfAbsent(getTableName(), id);
    }

    @Override
    public boolean forEach(Predicate<String> predicate) {
        return getDbHelper().executeRead((getDbHelper().isRowTenancy()
                ? "SELECT id FROM %s"
                : "SELECT id FROM %s WHERE namespace = ?")
                        .formatted(getTableName()),
                ps -> {
                    if (getDbHelper().isRowTenancy()) {
                        ps.setString(1, getDbHelper().getNamespace());
                    }
                },
                rs -> {
                    while (rs.next()) {
                        if (!predicate.test(rs.getString(1))) {
                            return false;
                        }
                    }
                    return true;
                });
    }
}
