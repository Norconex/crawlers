/* Copyright 2021-2022 Norconex Inc.
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

import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Map.Entry;

import com.norconex.commons.lang.map.MapUtil;
import com.norconex.commons.lang.text.StringUtil;
import com.norconex.crawler.core.store.DataStoreException;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Utility wrapper to help with minimal database data type compatibility
 * without having to rely on ORM software.
 * </p>
 */
@Slf4j
@Builder(builderMethodName = "_builder", access = AccessLevel.PUBLIC)
final class TableAdapter {

    private static final TableAdapterBuilder DEFAULT = _builder();
    private static final Map<String, TableAdapterBuilder> BUILDERS =
            MapUtil.toMap(
                "DERBY",
                    _builder()
                    .jsonType("CLOB")
                    .upsertSql("""
                        MERGE INTO <table> AS t
                        USING (VALUES (
                            CAST(? AS %s),
                            CAST(? AS %s),
                            CAST(? AS %s)
                        )) AS s (id, modified, json)
                        ON t.id = s.id
                        WHEN MATCHED THEN
                          UPDATE SET
                            t.modified = s.modified,
                            t.json = s.json
                        WHEN NOT MATCHED THEN
                          INSERT (id, modified, json)
                          VALUES (s.id, s.modified, s.json)
                        """),
                "DB2",
                    _builder()
                    .jsonType("CLOB")
                    .upsertSql("""
                        MERGE INTO <table> AS t
                        USING (VALUES (
                            CAST(? AS %s),
                            CAST(? AS %s),
                            CAST(? AS %s)
                        )) AS s (id, modified, json)
                        ON t.id = s.id
                        WHEN MATCHED THEN
                          UPDATE SET
                            t.modified = s.modified,
                            t.json = s.json
                        WHEN NOT MATCHED THEN
                          INSERT (id, modified, json)
                          VALUES (s.id, s.modified, s.json)
                        """),
                "H2",
                    _builder()
                    .jsonType("CLOB"),
                "MARIADB",
                    _builder()
                    .jsonType("LONGTEXT")
                    .upsertSql("""
                        INSERT INTO <table> (id, modified, json)
                        VALUES (
                            CAST(? AS %s),
                            CAST(? AS %s),
                            CAST(? AS %s)
                        )
                        ON DUPLICATE KEY UPDATE
                            modified = VALUES(modified),
                            json = VALUES(json)
                        """),
                "MYSQL",
                    _builder()
                    .jsonType("LONGTEXT")
                    .upsertSql("""
                        INSERT INTO <table> (id, modified, json)
                        VALUES (
                            CAST(? AS %s),
                            CAST(? AS %s),
                            CAST(? AS %s)
                        )
                        ON DUPLICATE KEY UPDATE
                            modified = VALUES(modified),
                            json = VALUES(json)
                        """),
                "ORACLE",
                    _builder()
                    .idType("VARCHAR2")
                    .jsonType("CLOB")
                    .upsertSql("""
                        MERGE INTO <table> t
                        USING (SELECT
                            CAST(? AS %s) AS id,
                            CAST(? AS %s) AS modified,
                            CAST(? AS %s) AS json
                          FROM dual
                        ) s
                        ON (t.id = s.id)
                        WHEN MATCHED THEN
                          UPDATE SET
                            t.modified = s.modified,
                            t.json = s.json
                        WHEN NOT MATCHED THEN
                          INSERT (id, modified, json)
                          VALUES (s.id, s.modified, s.json)
                        """),
                "POSTGRESQL",
                    _builder()
                    .upsertSql("""
                        INSERT INTO <table> (id, modified, json)
                        VALUES (
                            CAST(? AS %s),
                            CAST(? AS %s),
                            CAST(? AS %s)
                        )
                        ON CONFLICT (id)
                        DO UPDATE SET
                            modified = EXCLUDED.modified,
                            json = EXCLUDED.json
                        """),
                "SQLITE",
                    _builder()
                    .modifiedType("TEXT")
                    .jsonType("TEXT")
                    .upsertSql("""
                        INSERT OR REPLACE INTO <table> (id, modified, json)
                        VALUES (
                            CAST(? AS %s),
                            CAST(? AS %s),
                            CAST(? AS %s)
                        )
                        """),
                "SQLSERVER",
                    _builder()
                    .modifiedType("DATETIME")
                    .jsonType("NTEXT")
                    .upsertSql("""
                        MERGE INTO <table> AS t
                        USING (VALUES (
                            CAST(? AS %s),
                            CAST(? AS %s),
                            CAST(? AS %s)
                        )) AS s (id, modified, json)
                        ON t.id = s.id
                        WHEN MATCHED THEN
                          UPDATE SET
                            t.modified = s.modified,
                            t.json = s.json
                        WHEN NOT MATCHED THEN
                          INSERT (id, modified, json)
                          VALUES (s.id, s.modified, s.json)
                        """),
                "SYBASE", DEFAULT
        );

    @Default
    private final String idType = "VARCHAR";
    @Default
    private final String modifiedType = "TIMESTAMP";
    @Default
    private final String jsonType = "TEXT";
    // Has to have: <table> plus three %s for: id, modified, json
    @Default
    private final String upsertSql = """
            MERGE INTO <table> AS t
            USING (SELECT
                CAST(? AS %s) AS id,
                CAST(? AS %s) AS modified,
                CAST(? AS %s) AS json
            ) AS s
            ON t.id = s.id
            WHEN MATCHED THEN
              UPDATE SET
                t.modified = s.modified,
                t.json = s.json
            WHEN NOT MATCHED THEN
              INSERT (id, modified, json)
              VALUES (s.id, s.modified, s.json)
            """;


    private static final int ID_MAX_LENGTH = 2048;

    String serializableId(String id) {
        try {
            return StringUtil.truncateBytesWithHash(
                    id, StandardCharsets.UTF_8, ID_MAX_LENGTH);
        } catch (CharacterCodingException e) {
            throw new DataStoreException("Could not truncate ID: " + id, e);
        }
    }
    String idType() {
        return idType + "(" + ID_MAX_LENGTH + ")";
    }
    String modifiedType() {
        return modifiedType;
    }
    String jsonType() {
        return jsonType;
    }
    String upsertSql(String tableName) {
        return upsertSql
                .replace("<table>", tableName)
                .formatted(
                    idType(),
                    modifiedType(),
                    jsonType());
    }

    static TableAdapterBuilder builder(String jdbcUrlOrDataSource) {
        if (jdbcUrlOrDataSource == null) {
            LOG.warn("Unrecognized database '{}'. "
                    + "Will try using generic database settings.",
                    jdbcUrlOrDataSource);
            return DEFAULT;
        }
        var upper = jdbcUrlOrDataSource.toUpperCase();

        return BUILDERS
                .entrySet()
                .stream()
                .filter(en -> upper.contains(en.getKey()))
                .findFirst()
                .map(Entry::getValue)
                .orElse(DEFAULT);
    }
}
