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

import static org.apache.commons.lang3.StringUtils.removeStart;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.commons.lang.xml.XMLConfigurable;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.store.DataStoreException;
import com.norconex.crawler.core.store.IDataStore;
import com.norconex.crawler.core.store.IDataStoreEngine;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

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
 *      id plus the crawler id, each followed by an underscore character.)
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
public class JdbcDataStoreEngine
        implements IDataStoreEngine, XMLConfigurable {

    private static final Logger LOG =
            LoggerFactory.getLogger(JdbcDataStoreEngine.class);

    private static final String STORE_TYPES_NAME = "_storetypes";

    // Non-configurable:
    private HikariDataSource datasource;
    private String tablePrefix;
    // table id field is store name
    private JdbcDataStore<String> storeTypes;
    private TableAdapter tableAdapter;

    // Configurable:
    private Properties configProperties = new Properties();
    private String varcharType;
    private String timestapType;
    private String textType;

    public Properties getConfigProperties() {
        return configProperties;
    }
    public void setConfigProperties(Properties configProperties) {
        this.configProperties = configProperties;
    }
    public String getTablePrefix() {
        return tablePrefix;
    }
    public void setTablePrefix(String tablePrefix) {
        this.tablePrefix = tablePrefix;
    }
    public String getVarcharType() {
        return varcharType;
    }
    public void setVarcharType(String varcharType) {
        this.varcharType = varcharType;
    }
    public String getTimestapType() {
        return timestapType;
    }
    public void setTimestapType(String timestapType) {
        this.timestapType = timestapType;
    }
    public String getTextType() {
        return textType;
    }
    public void setTextType(String textType) {
        this.textType = textType;
    }

    @Override
    public void init(Crawler crawler) {
        // create a clean table name prefix to avoid collisions in case
        // multiple crawlers use the same DB.
        if (tablePrefix == null) {
            tablePrefix = crawler.getCrawlSession().getId()
                    + "_" + crawler.getId() + "_";
        }

        // create data source
        datasource = new HikariDataSource(
                new HikariConfig(configProperties.toProperties()));

        tableAdapter = resolveTableAdapter();

        // store types for each table
        storeTypes = new JdbcDataStore<>(this, STORE_TYPES_NAME, String.class);
    }

    private TableAdapter resolveTableAdapter() {
        return TableAdapter.detect(StringUtils.firstNonBlank(
                datasource.getJdbcUrl(), datasource.getDriverClassName()))
            .withIdType(varcharType)
            .withModifiedType(timestapType)
            .withJsonType(textType);
    }

    @Override
    public boolean clean() {
        // the table storing the store types is not returned by getStoreNames
        // so we have to explicitly delete it.
        var names = getStoreNames();
        var hasStores = !names.isEmpty();
        if (hasStores) {
            names.stream().forEach(this::dropStore);
        }
        dropStore(STORE_TYPES_NAME);
        close();
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

    @Override
    public <T> IDataStore<T> openStore(
            String storeName, Class<? extends T> type) {
        storeTypes.save(storeName, type.getName());
        return new JdbcDataStore<>(this, storeName, type);
    }

    @Override
    public boolean dropStore(String storeName) {
        var tableName = tableName(storeName);
        if (!tableExist(tableName)) {
            return false;
        }
        try (var conn = datasource.getConnection()) {
            try (var stmt = conn.createStatement()) {
                stmt.executeUpdate("DROP TABLE " + tableName);
                if (!conn.getAutoCommit()) {
                    conn.commit();
                }
            }
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not drop table '" + tableName + "'.", e);
        }

        if (STORE_TYPES_NAME.equals(storeName)) {
            storeTypes = null;
        } else {
            storeTypes.delete(storeName);
        }
        return true;
    }

    @Override
    public boolean renameStore(IDataStore<?> dataStore, String newStoreName) {
        JdbcDataStore<?> jdbcStore = (JdbcDataStore<?>) dataStore;
        var oldStoreName = jdbcStore.getName();
        var existed = ((JdbcDataStore<?>) dataStore).rename(newStoreName);
        storeTypes.delete(oldStoreName);
        storeTypes.save(newStoreName, jdbcStore.getType().getName());
        return existed;
    }

    @Override
    public Set<String> getStoreNames() {
        Set<String> names = new HashSet<>();
        try (var conn = datasource.getConnection()) {
            try (var rs = conn.getMetaData().getTables(
                    null, null, "%", new String[]{"TABLE"})) {
                while (rs.next()) {
                    var tableName = rs.getString(3);
                    if (startsWithIgnoreCase(tableName, tablePrefix)) {
                        // only add if not the table holding store types
                        var storeName = removeStart(tableName, tablePrefix);
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

    @Override
    public void loadFromXML(XML xml) {
        var nodes = xml.getXMLList("datasource/property");
        for (XML node : nodes) {
            var name = node.getString("@name");
            var value = node.getString(".");
            configProperties.add(name, value);
        }
        setTablePrefix(xml.getString("tablePrefix", getTablePrefix()));
        setVarcharType(
                xml.getString("dataTypes/varchar/@use", getVarcharType()));
        setTimestapType(
                xml.getString("dataTypes/timestamp/@use", getTimestapType()));
        setTextType(xml.getString("dataTypes/text/@use", getTextType()));
    }

    @Override
    public void saveToXML(XML xml) {
        var xmlDatasource = xml.addElement("datasource");
        for (Entry<String, List<String>> entry : configProperties.entrySet()) {
            var values = entry.getValue();
            for (String value : values) {
                if (value != null) {
                    xmlDatasource.addElement("property", value)
                            .setAttribute("name", entry.getKey());
                }
            }
        }
        xml.addElement("tablePrefix", getTablePrefix());
        var dtXML = xml.addElement("dataTypes");
        dtXML.addElement("varchar").setAttribute("use", getVarcharType());
        dtXML.addElement("timestamp").setAttribute("use", getTimestapType());
        dtXML.addElement("text").setAttribute("use", getTextType());
    }

    TableAdapter getTableAdapter() {
        return tableAdapter;
    }

    Connection getConnection() {
        try {
            return datasource.getConnection();
        } catch (SQLException e) {
            throw new DataStoreException(
                    "Could not get database connection.", e);
        }
    }
    String tableName(String storeName) {
        var n = tablePrefix + storeName.trim();
        n = n.replaceFirst("(?i)^[^a-z]", "x");
        return n.replaceAll("\\W+", "_");
    }

    boolean tableExist(String tableName) {
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
}
