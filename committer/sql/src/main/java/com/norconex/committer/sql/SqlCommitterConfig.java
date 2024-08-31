/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.committer.sql;

import java.io.Serializable;

import com.norconex.committer.core.batch.BaseBatchCommitterConfig;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.security.Credentials;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * SQL Committer configuration.
 * </p>
 */
@Data
@Accessors(chain = true)
public class SqlCommitterConfig
        extends BaseBatchCommitterConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Default SQL content field */
    public static final String DEFAULT_SQL_CONTENT_FIELD = "content";
    /** Default multi-value join string */
    public static final String DEFAULT_MULTI_VALUES_JOINER = "|";

    private String driverPath;
    private String driverClass;
    private String connectionUrl;
    private final Credentials credentials = new Credentials();
    private final Properties properties = new Properties();

    private String tableName;
    private String primaryKey;
    private String createTableSQL;
    private String createFieldSQL;

    private boolean fixFieldNames;
    private boolean fixFieldValues;
    private String multiValuesJoiner = DEFAULT_MULTI_VALUES_JOINER;

    private String targetContentField = DEFAULT_SQL_CONTENT_FIELD;

    public Credentials getCredentials() {
        return credentials;
    }

    public SqlCommitterConfig setCredentials(Credentials credentials) {
        this.credentials.copyFrom(credentials);
        return this;
    }

    public Properties getProperties() {
        return properties;
    }

    public SqlCommitterConfig setProperties(Properties properties) {
        CollectionUtil.setAll(this.properties, properties);
        return this;
    }
}
