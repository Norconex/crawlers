/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.jdbc;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;
import java.util.stream.Stream;

import javax.sql.DataSource;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/**
 * Database dialect used to create database vendor-specific queries. If your
 * database is not represented, there is the possibility of it being
 * supported if it shares the same SQL syntax as one of the supported dialects.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public enum JdbcDialect {

    DERBY(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data TEXT,
                seq INT GENERATED BY DEFAULT AS IDENTITY
            );
            CREATE INDEX <table>_idx_seq ON <table> (seq);
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            MERGE INTO <table> AS t
            USING (VALUES (
                CAST(? AS VARCHAR),
                CAST(? AS TEXT)
            )) AS s (id, data)
            ON t.id = s.id
            WHEN MATCHED THEN
              UPDATE SET t.data = s.data
            WHEN NOT MATCHED THEN
              INSERT (id, data) VALUES (s.id, s.data)
            """),
    DB2(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data CLOB,
                seq INTEGER GENERATED ALWAYS
                    AS IDENTITY (START WITH 1 INCREMENT BY 1)
            );
            CREATE INDEX <table>_idx_seq ON <table> (seq);
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            MERGE INTO <table> AS t
            USING (VALUES (
                CAST(? AS VARCHAR),
                CAST(? AS CLOB)
            )) AS s (id, data)
            ON t.id = s.id
            WHEN MATCHED THEN
              UPDATE SET t.data = s.data
            WHEN NOT MATCHED THEN
              INSERT (id, data) VALUES (s.id, s.data)
            """),
    H2(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data CLOB,
                seq INT AUTO_INCREMENT,
                UNIQUE (seq)
            );
            CREATE INDEX <table>_idx_seq
            ON <table> (seq);
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            MERGE INTO <table> AS t
            USING (SELECT
                CAST(? AS VARCHAR) AS id,
                CAST(? AS CLOB) AS data
            ) AS s
            ON t.id = s.id
            WHEN MATCHED THEN
              UPDATE SET t.data = s.data
            WHEN NOT MATCHED THEN
              INSERT (id, data) VALUES (s.id, s.data)
            """),
    MARIADB(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data LONGTEXT,
                seq INT AUTO_INCREMENT,
                INDEX <table>_idx_seq (seq)
            );
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            INSERT INTO <table> (id, data)
            VALUES (
                CAST(? AS VARCHAR),
                CAST(? AS LONGTEXT)
            )
            ON DUPLICATE KEY UPDATE data = VALUES(data)
            """),
    MYSQL(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data LONGTEXT,
                seq INT AUTO_INCREMENT,
                INDEX <table>_idx_seq (seq)
            )
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            INSERT INTO <table> (id, data)
            VALUES (
                CAST(? AS VARCHAR),
                CAST(? AS LONGTEXT)
            )
            ON DUPLICATE KEY UPDATE data = VALUES(data)
            """),
    ORACLE(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR2(%s) PRIMARY KEY,
                data CLOB,
                seq NUMBER GENERATED BY DEFAULT AS IDENTITY
            );
            CREATE INDEX <table>_idx_seq
            ON <table> (seq);
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            MERGE INTO <table> t
            USING (SELECT
                CAST(? AS VARCHAR2) AS id,
                CAST(? AS CLOB) AS data
              FROM dual
            ) s
            ON (t.id = s.id)
            WHEN MATCHED THEN
              UPDATE SET t.data = s.data
            WHEN NOT MATCHED THEN
              INSERT (id, data)
              VALUES (s.id, s.data)
            """),
    POSTGRESQL(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data TEXT,
                seq SERIAL
            );
            CREATE INDEX <table>_idx_seq
            ON <table> (seq);
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            MERGE INTO <table> t
            USING (SELECT
                CAST(? AS VARCHAR) AS id,
                CAST(? AS TEXT) AS data
            ) AS s
            ON t.id = s.id
            WHEN MATCHED AND t.data IS DISTINCT FROM s.data THEN
              UPDATE SET data = s.data
            WHEN NOT MATCHED THEN
              INSERT (id, data)
              VALUES (s.id, s.data)
            """),
        //TODO write a unit test for the returned affected row count
    SQLITE(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data TEXT,
                seq INTEGER AUTOINCREMENT
            );
            CREATE INDEX <table>_idx_seq
            ON <table> (seq);
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            INSERT OR REPLACE INTO <table> (id, data)
            VALUES (
                CAST(? AS VARCHAR),
                CAST(? AS TEXT)
            )
            """),
    SQLSERVER(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data NTEXT,
                seq INT IDENTITY(1,1),
                INDEX <table>_idx_seq (seq)
            );
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            MERGE INTO <table> AS t
            USING (VALUES (
                CAST(? AS VARCHAR),
                CAST(? AS NTEXT)
            )) AS s (id, data)
            ON t.id = s.id
            WHEN MATCHED THEN
              UPDATE SET t.data = s.data
            WHEN NOT MATCHED THEN
              INSERT (id, data)
              VALUES (s.id, s.data)
            """),
    SYBASE(
            // Create table:
            """
            CREATE TABLE <table> (
                id VARCHAR(%s) PRIMARY KEY,
                data TEXT,
                seq INT IDENTITY,
                INDEX <table>_idx_seq (seq)
            );
            """.formatted(JdbcDialect.ID_MAX_LENGTH),
            // Upsert:
            """
            MERGE INTO <table> AS t
            USING (SELECT
                CAST(? AS VARCHAR) AS id,
                CAST(? AS TEXT) AS data
            ) AS s
            ON t.id = s.id
            WHEN MATCHED THEN
              UPDATE SET t.data = s.data
            WHEN NOT MATCHED THEN
              INSERT (id, data)
              VALUES (s.id, s.data)
            """),
    ;

    static final int ID_MAX_LENGTH = 2048;

    @Getter
    private final String createTableSql;
    @Getter
    private final String upsertSql;

    public static Optional<JdbcDialect> of(@NonNull DataSource dataSource)
            throws SQLException {
        return of(dataSource.getConnection());
    }
    public static Optional<JdbcDialect> of(@NonNull Connection connection)
            throws SQLException {
        return of(connection.getMetaData().getDatabaseProductName());
    }
    public static Optional<JdbcDialect> of(@NonNull String productName) {
        var name = productName.replaceAll("[^A-Za-z0-9]", "");
        return Stream
                .of(JdbcDialect.values())
                .filter(d -> d.name().equalsIgnoreCase(name))
                .findFirst();
    }
}
