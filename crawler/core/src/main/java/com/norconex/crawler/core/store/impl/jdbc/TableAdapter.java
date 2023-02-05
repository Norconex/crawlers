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

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.store.DataStoreException;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.commons.lang.text.StringUtil;

/**
 * <p>
 * Utility wrapper to help with minimal database data type compatibility
 * without having to rely on ORM software.
 * </p>
 */
final class TableAdapter {

    private static final TableAdapter DEFAULT =
            of("VARCHAR",  "TIMESTAMP", "TEXT");
    private static final Map<String, TableAdapter> ADAPTERS = MapUtil.toMap(
            "DERBY", DEFAULT.withJsonType("CLOB"),
            "DB2", DEFAULT.withJsonType("CLOB"),
            "H2", DEFAULT.withJsonType("CLOB"),
            "MYSQL", DEFAULT.withJsonType("LONGTEXT"),
            "ORACLE", of("VARCHAR2",  "TIMESTAMP", "CLOB"),
            "POSTGRESQL", DEFAULT,
            "SQLSERVER", of("VARCHAR",  "DATETIME",  "NTEXT"),
            "SYBASE", DEFAULT
    );

    private static final int ID_MAX_LENGTH = 2048;

    private final String idType;
    private final String modifiedType;
    private final String jsonType;
    private TableAdapter(String idType, String modifiedType, String jsonType) {
        this.idType = idType;
        this.modifiedType = modifiedType;
        this.jsonType = jsonType;
    }

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

    TableAdapter withIdType(String idType) {
        if (StringUtils.isBlank(idType)) {
            return this;
        }
        return new TableAdapter(idType, modifiedType, jsonType);
    }
    TableAdapter withModifiedType(String modifiedType) {
        if (StringUtils.isBlank(modifiedType)) {
            return this;
        }
        return new TableAdapter(idType, modifiedType, jsonType);
    }
    TableAdapter withJsonType(String jsonType) {
        if (StringUtils.isBlank(jsonType)) {
            return this;
        }
        return new TableAdapter(idType, modifiedType, jsonType);
    }
    static TableAdapter of(
            String idType, String modifiedType, String jsonType) {
        return new TableAdapter(idType, modifiedType, jsonType);
    }

    static TableAdapter detect(String jdbcUrlOrDataSource) {
        if (jdbcUrlOrDataSource == null) {
            return DEFAULT;
        }
        String upper = jdbcUrlOrDataSource.toUpperCase();
        return ADAPTERS
                .entrySet()
                .stream()
                .filter(en -> upper.contains(en.getKey()))
                .findFirst()
                .map(Entry::getValue)
                .orElse(DEFAULT);
    }
}
