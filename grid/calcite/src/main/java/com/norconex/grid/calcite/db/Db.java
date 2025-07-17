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
package com.norconex.grid.calcite.db;

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.apache.commons.lang3.function.FailableConsumer;
import org.apache.commons.lang3.function.FailableFunction;

import com.norconex.commons.lang.text.StringUtil;
import com.norconex.grid.calcite.MultiTenancyMode;
import com.norconex.grid.calcite.conn.SqlConnectionProvider;
import com.norconex.grid.core.GridException;

import lombok.Getter;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Db {

    public static final int ID_MAX_LENGTH = 2048;
    public static final int NS_MAX_LENGTH = 255;

    private final SqlConnectionProvider connectionProvider;
    private final TransactionManager transactionManager;
    @Getter
    private final MultiTenancyMode multiTenancyMode;
    @Getter
    private final String namespace;

    public Db(
            @NonNull SqlConnectionProvider connectionProvider,
            @NonNull MultiTenancyMode multiTenancyMode,
            String namespace) {
        this.namespace = namespace;
        this.connectionProvider = connectionProvider;
        transactionManager = new TransactionManager(connectionProvider);
        this.multiTenancyMode = multiTenancyMode;
    }

    public Connection getConnection() {
        try {
            return connectionProvider.getConnection();
        } catch (SQLException e) {
            throw new GridException("Could not get connection.", e);
        }
    }

    public boolean isRowTenancy() {
        return this.multiTenancyMode == MultiTenancyMode.ROW;
    }

    public void dropTableIfExists(String tableName) {
        DbWriteUtil.dropTableIfExists(this, tableName);
    }

    /**
     * Creates a table only if it doesn't exist, handling possible concurrency
     * issues in multi-nodes environment.
     */
    public void createTableIfNotExists(String createSQL, String tableName) {
        DbWriteUtil.createTableIfNotExists(this, createSQL, tableName);
    }

    public boolean isEmpty(String tableName) {
        return DbReadUtil.isEmpty(this, tableName);
    }

    public void deleteAllRows(String tableName) {
        DbWriteUtil.deleteAllRows(this, tableName);
    }

    public boolean deleteById(String tableName, String id) {
        return DbWriteUtil.deleteById(this, tableName, id);
    }

    public boolean tableExists(String tableName) {
        return DbReadUtil.tableExists(this, tableName);
    }

    public long count(String tableName) {
        return DbReadUtil.count(this, tableName);
    }

    public boolean contains(String tableName, String fieldName, String value) {
        return DbReadUtil.contains(this, tableName, fieldName, value);
    }

    public <R> R runInTransactionAndReturn(
            FailableFunction<Connection, R, SQLException> function) {
        return transactionManager.runInTransaction(function);
    }

    public void runInTransaction(
            FailableConsumer<Connection, SQLException> consumer) {
        transactionManager.runInTransaction(conn -> {
            consumer.accept(conn);
            return null;
        });
    }

    public <T> T getById(String tableName, String key, Class<T> type) {
        return DbReadUtil.getById(this, tableName, key, type);
    }

    public boolean insertIfAbsent(String tableName, String id, Object value) {
        return DbWriteUtil.insertIfAbsent(this, tableName, id, value);
    }

    public boolean insertIfAbsent(String tableName, String id) {
        return DbWriteUtil.insertIfAbsent(this, tableName, id);
    }

    public boolean upsert(String tableName, String id, Object value) {
        return DbWriteUtil.upsert(this, tableName, id, value);
    }

    public void close() {
        connectionProvider.close();
    }

    // just so we don't have to quote
    public String toSafeName(String name) {
        String nm = name.toLowerCase().replaceAll("\\W+", "_");
        nm = nm.replaceFirst("^[\\d_]+", "");
        if (nm.isEmpty()) {
            throw new IllegalArgumentException("Calcite grid store name must "
                    + "contain at least one alphabetical character");
        }
        try {
            if (SqlReservedWords.isReservedWord(
                    connectionProvider.getConnection(), nm)) {
                nm += "_safe";
                LOG.warn("Table name is/has a database reserved word. Will "
                        + "use {} instead.", nm);
            }
        } catch (SQLException e) {
            LOG.warn("Cannot get reserved words from database.", e);
        }
        if (!nm.equals(name)) {
            LOG.warn("Table name has been adjusted from {} to {}.", name, nm);
        }
        return nm;
    }

    public <T> T poll(String tableName, Class<T> type) {
        return DbReadUtil.poll(this, tableName, type);
    }

    public static String rightSizeId(String id) {
        try {
            return StringUtil.truncateBytesWithHash(
                    id, StandardCharsets.UTF_8, ID_MAX_LENGTH);
        } catch (CharacterCodingException e) {
            throw new GridException("Could not truncate ID: " + id, e);
        }
    }

    public int executeWrite(
            String sql, FailableConsumer<PreparedStatement, SQLException> c) {
        return DbWriteUtil.executeWrite(this, sql, c);
    }

    public <R> R executeRead(
            String sql,
            FailableConsumer<PreparedStatement, SQLException> psc,
            FailableFunction<ResultSet, R, SQLException> rsf) {
        return DbReadUtil.executeRead(this, sql, psc, rsf);
    }
}
