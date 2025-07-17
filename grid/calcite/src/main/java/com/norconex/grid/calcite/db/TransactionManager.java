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

import java.sql.Connection;
import java.sql.SQLException;

import org.apache.commons.lang3.function.FailableFunction;

import com.norconex.grid.calcite.conn.SqlConnectionProvider;
import com.norconex.grid.core.GridException;

class TransactionManager {
    private final SqlConnectionProvider connectionProvider;
    private final ThreadLocal<Connection> connectionThreadLocal =
            new ThreadLocal<>();

    public TransactionManager(SqlConnectionProvider connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public <R> R runInTransaction(
            FailableFunction<Connection, R, SQLException> function) {
        var existingConnection = connectionThreadLocal.get();
        var isNestedTransaction = existingConnection != null;

        // Nested call, just run the callable
        if (isNestedTransaction) {
            try {
                return function.apply(existingConnection);
            } catch (Exception e) {
                throw new GridException(e);
            }
        }
        // Outer call, call within a new transaction
        try (var connection = connectionProvider.getConnection()) {
            return callWithNewTransaction(function, connection);
        } catch (Exception e) {
            throw new GridException("Transaction failed", e);
        }
    }

    private <R> R callWithNewTransaction(
            FailableFunction<Connection, R, SQLException> function,
            Connection connection)
            throws SQLException {
        var origAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        connectionThreadLocal.set(connection);
        try {
            var result = function.apply(connection);
            connection.commit();
            return result;
        } catch (Exception e) {
            connection.rollback();
            throw new GridException(e);
        } finally {
            connectionThreadLocal.remove();
            connection.setAutoCommit(origAutoCommit);
        }
    }
}
