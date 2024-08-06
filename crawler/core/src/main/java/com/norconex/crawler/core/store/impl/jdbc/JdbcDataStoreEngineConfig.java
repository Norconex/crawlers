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

import java.util.Properties;

import lombok.Data;
import lombok.experimental.Accessors;

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
@Data
@Accessors(chain = true)
public class JdbcDataStoreEngineConfig {

    private String tablePrefix;
    private JdbcDialect dialect;
    private String createTableSql;
    private String upsertSql;

    private Properties properties = new Properties();
//    private String varcharType;
//    private String timestampType;
//    private String textType;
}
