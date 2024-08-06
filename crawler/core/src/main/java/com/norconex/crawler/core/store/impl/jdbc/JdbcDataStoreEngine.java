/* Copyright 2021-2023 Norconex Inc.
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

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.removeStartIgnoreCase;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.core.store.DataStoreException;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * Data store engine using a JDBC-compatible database for storing
 * crawl data.
 * </p>
 * <h3>Database JDBC driver</h3>
 * <p>
 * To use this data store engine, you need its JDBC database driver
 * on the classpath.
 * </p>
 * <h3>Database datasource configuration</h3>
 * <p>
 * This JDBC data store engine uses
 * <a href="https://github.com/brettwooldridge/HikariCP">Hikari</a> as the JDBC
 * datasource implementation, which provides efficient connection-pooling.
 * Refer to
 * <a href="https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby">
 * Hikari's documentation</a> for all configuration options.  The Hikari options
 * are passed as-is, via <code>datasource</code> properties as shown below.
 * </p>
 * <h3>Data types</h3>
 * <p>
 * This class only use a few data types to store its data in a generic way.
 * It will try to detect what data type to use for your database. If you
 * get errors related to field data types not being supported, you have
 * the option to redefined them.
 * </p>
 *
 * {@nx.xml.usage
 * <dataStoreEngine class="com.norconex.crawler.core.store.impl.jdbc.JdbcDataStoreEngine">
 *   <!-- Hikari datasource configuration properties: -->
 *   <datasource>
 *     <property name="(property name)">(property value)</property>
 *   </datasource>
 *   <tablePrefix>
 *     (Optional prefix used for table creation. Default is the collector
 *      id plus the crawler id, each followed by an underscore character.
 *      The value is first modified to convert spaces to underscores, and
 *      to strip unsupported characters. The supported
 *      characters are: alphanumeric, period, and underscore.
 *      )
 *   </tablePrefix>
 *   <!--
 *     Optionally overwrite default SQL data type used.  You should only
 *     use if you get data type-related errors.
 *     -->
 *   <dataTypes>
 *     <varchar   use="(equivalent data type for your database)" />
 *     <timestamp use="(equivalent data type for your database)" />
 *     <text      use="(equivalent data type for your database)" />
 *   </dataTypes>
 * </dataStoreEngine>
 * }
 *
 * {@nx.xml.example
 * <dataStoreEngine class="JdbcDataStoreEngine">
 *   <datasource>
 *     <property name="jdbcUrl">jdbc:mysql://localhost:33060/sample</property>
 *     <property name="username">dbuser</property>
 *     <property name="password">dbpwd</property>
 *     <property name="connectionTimeout">1000</property>
 *   </datasource>
 * </dataStoreEngine>
 * }
 * <p>
 * The above example contains basic settings for creating a MySQL data source.
 * </p>
 */
@Slf4j
@EqualsAndHashCode
@ToString
public class JdbcDataStoreEngine
        implements DataStoreEngine, Configurable<JdbcDataStoreEngineConfig> {

    private static final String STORE_TYPES_NAME = "storetypes";

    // Non-configurable:
    private HikariDataSource datasource;
    // table id field is store name
    private JdbcDataStore<String> storeTypes;
    private JdbcDialect dialect;
    private String safeTablePrefix;

    @Getter
    private JdbcDataStoreEngineConfig configuration =
            new JdbcDataStoreEngineConfig();

    @Override
    public void init(Crawler crawler) {
        safeTablePrefix = safeTableName(isBlank(
                configuration.getTablePrefix())
            ? crawler.getCrawlSession().getId() + "_" + crawler.getId() + "_"
            : configuration.getTablePrefix());

        // create data source
        datasource = new HikariDataSource(
                new HikariConfig(configuration.getProperties()));
        try {
            dialect = JdbcDialect.of(datasource).orElse(null);
            LOG.info("Detected JDBC dialect: " + dialect);
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not establish JDBC dialect.", e);
        }

        // to store types for each table
        storeTypes = createStore(STORE_TYPES_NAME, String.class);
    }


    @Override
    public boolean clean() {
        var names = getStoreNames();
        var hasStores = !names.isEmpty();
        if (hasStores) {
            names.stream().forEach(this::dropStore);
        }
        // the table storing the store types is not returned by getStoreNames
        // so we have to explicitly delete it.
        dropStore(STORE_TYPES_NAME);
        return hasStores;
    }

    @Override
    public void close() {
        if (datasource != null) {
            LOG.info("Closing JDBC data store engine datasource...");
            datasource.close();
            LOG.info("JDBC Data store engine datasource closed.");
            datasource = null;
        } else {
            LOG.info("JDBC Data store engine datasource already closed.");
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> DataStore<T> openStore(
            String storeName, Class<? extends T> type) {
        storeTypes.save(storeName, type.getName());
        return (DataStore<T>) createStore(storeName, type);
    }

    @Override
    public boolean dropStore(String storeName) {
        var tableName = toTableName(storeName);
        if (!tableExist(tableName)) {
            return false;
        }
        try (var conn = datasource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE %s".formatted(tableName));
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }
            }
            LOG.info("Dropped table: " + tableName);
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not drop table '" + tableName + "'.", e);
        }

        if (STORE_TYPES_NAME.equalsIgnoreCase(storeName)) {
            storeTypes = null;
        } else {
            storeTypes.delete(storeName);
        }
        return true;
    }

    @Override
    public boolean renameStore(DataStore<?> dataStore, String newStoreName) {
        JdbcDataStore<?> jdbcStore = (JdbcDataStore<?>) dataStore;
        var oldStoreName = jdbcStore.getName();
        var existed = ((JdbcDataStore<?>) dataStore).rename(newStoreName);
        storeTypes.delete(oldStoreName);
        storeTypes.save(newStoreName, jdbcStore.getType().getName());
        return existed;
    }

    @JsonIgnore
    @Override
    public Set<String> getStoreNames() {
        Set<String> names = new HashSet<>();
        try (var conn = datasource.getConnection()) {
            try (var rs = conn.getMetaData().getTables(
                    null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    var tableName = rs.getString(3);
                    if (startsWithIgnoreCase(
                            tableName, safeTablePrefix)) {
                        // only add if not the table holding store types
                        var storeName = removeStartIgnoreCase(
                                tableName, safeTablePrefix);
                        if (!STORE_TYPES_NAME.equalsIgnoreCase(storeName)) {
                            names.add(storeName);
                        }
                    }
                }
            }
            return names;
        } catch (SQLException e) {
            throw new DataStoreException("Could not get store names.", e);
        }
    }

    @JsonIgnore
    @Override
    public Optional<Class<?>> getStoreType(String storeName) {
        if (storeName == null) {
            return Optional.empty();
        }
        var typeStr = storeTypes.find(storeName);
        if (typeStr.isPresent()) {
            try {
                return Optional.ofNullable(ClassUtils.getClass(typeStr.get()));
            } catch (ClassNotFoundException e) {
                throw new DataStoreException(
                        "Could not determine type of: " + storeName, e);
            }
        }
        return Optional.empty();
    }

    @JsonIgnore
    Connection getConnection() {
        try {
            return datasource.getConnection();
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not get database connection.", e);
        }
    }

    String toTableName(String storeName) {
        return safeTablePrefix + safeTableName(storeName);
    }

    boolean tableExist(String tableName) {
        //TODO check cluster if initializing... and if so, return true
        // since the one that does the init will take care of creating if not there
        try (var conn = datasource.getConnection()) {
            try (var rs = conn.getMetaData().getTables(
                    null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    if (rs.getString(3).equalsIgnoreCase(tableName)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not check if table '" + tableName + "' exists.", e);
        }
    }

    //--- Private methods ------------------------------------------------------

    private <T> JdbcDataStore<T> createStore(String storeName, Class<T> type) {
        var tableName = toTableName(storeName);
        return new JdbcDataStore<>(JdbcDataStore.StoreSettings
                .<T>builder()
                .engine(this)
                .storeName(storeName)
                .tableName(tableName)
                .type(type)
                .createTableSqlTemplate(resolveCreateTableSqlTemplate())
                .upsertSqlTemplate(resolveUpsertSqlTemplate())
                .build());
    }

    private String resolveCreateTableSqlTemplate() {
        var createSql = configuration.getCreateTableSql();
        if (StringUtils.isBlank(createSql)) {
            if (dialect == null) {
                throw new IllegalArgumentException("""
                    Could not resolve the table creation SQL statement for
                    your database. Either it is misconfigured, not yet
                    supported, or you need to configure the SQL statement
                    yourself.
                    """);
            }
            createSql = dialect.getCreateTableSql();
        }
        return createSql;
    }

    private String resolveUpsertSqlTemplate() {
        var upsertSql = configuration.getUpsertSql();
        if (StringUtils.isBlank(upsertSql)) {
            if (dialect == null) {
                throw new IllegalArgumentException("""
                    Could not resolve the table upsert SQL statement for
                    your database. Either it is misconfigured, not yet
                    supported, or you need to configure the SQL statement
                    yourself.
                    """);
            }
            upsertSql = dialect.getUpsertSql();
        }
        return upsertSql;
    }


    /**
     * Modifies the value to prevent SQL injection. Spaces are converted
     * to underscores, and unsupported characters are stripped. The supported
     * characters are: alphanumeric, period, and underscore.
     * @param tableName table name
     * @return safe table name
     */
    private static String safeTableName(String tableName) {
        var tn = StringUtils.trimToEmpty(tableName);
        tn = tn.replaceAll("\\s+", "_");
        tn = tn.replaceAll("[^_a-zA-Z0-9\\.]+", "");
        tn = tn.replaceFirst("^[^a-zA-Z]+", "");
        if (StringUtils.isBlank(tn)) {
            throw new DataStoreException("The table name contains no supported "
                    + "characters (alphanumeric, period, or underscore): "
                    + tableName);
        }
        return tn;
    }
}
