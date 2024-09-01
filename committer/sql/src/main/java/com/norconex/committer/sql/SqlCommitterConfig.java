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

    /**
     * Path to JDBC driver. Not required if already in classpath.
     */
    private String driverPath;
    /**
     * Class name of the JDBC driver to use.
     */
    private String driverClass;
    /**
     * JDBC connection URL.
     */
    private String connectionUrl;
    /**
     * Database credentials.
     */
    private final Credentials credentials = new Credentials();
    /**
     * Extra connection properties as per database vendor.
     */
    private final Properties properties = new Properties();

    /**
     * The target database table name where documents will be committed.
     */
    private String tableName;
    /**
     * The name of the table primary key field where the document
     * reference will be stored, unless it is already set by a field of
     * the same name in the source document. At a minimum, this "primaryKey"
     * field name should be "unique", ideally indexed.
     */
    private String primaryKey;
    /**
     * The CREATE statement used to create a table if it does not
     * already exist. If you need fields of specific data types,
     * specify them here. You can use the variable placeholders {tableName}
     * and {primaryKey} which will be replaced with the configuration option
     * of the same name. If you do not use those variables, make sure you use
     * the same names.
     */
    private String createTableSQL;
    /**
     * The ALTER statement used to create missing table fields.
     * The {tableName} variable will be replaced with
     * the configuration option of the same name. The {fieldName}
     * variable will be replaced by newly encountered field names.
     */
    private String createFieldSQL;

    /**
     * Whether to attempt preventing insertion errors by converting characters
     * that are not underscores or alphanumeric to underscores.
     * Will also remove all non-alphabetic characters that prefixes
     * a field name.
     */
    private boolean fixFieldNames;
    /**
     * Attempts to prevent insertion errors by truncating values
     * that are larger than their defined maximum field length.
     */
    private boolean fixFieldValues;
    /**
     * One or more characters to join multi-value fields. Default is "|".
     */
    private String multiValuesJoiner = DEFAULT_MULTI_VALUES_JOINER;

    /**
     * Table field name where to store the document content stream.
     * Make it empty or a self-closed tag if you do not want to store the
     * document content. Since document content can sometimes be quite
     * large, a CLOB field is usually advised.
     * If there is already a document field with the same name, that
     * document field takes precedence and the content stream is ignored.
     * Default is "content".
     */
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
