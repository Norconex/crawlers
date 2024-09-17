/* Copyright 2021-2024 Norconex Inc.
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
 * Configuration for {@link JdbcDataStoreEngine}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class JdbcDataStoreEngineConfig {

    /**
     * Optional prefix used for table creation. Default is the crawler id,
     * followed by an underscore character.
     * The value is first modified to convert spaces to underscores, and
     * to strip unsupported characters. The supported
     * characters are: alphanumeric, period, and underscore.
     */
    private String tablePrefix;

    /**
     * Explicitly set the JDBC dialect to use when generating SQL.
     * By default, an attempt is made to detect it automatically.
     * Not really useful if you provide your own SQLs for table creation
     * and upserts.
     */
    private JdbcDialect dialect;

    /**
     * <b>For advanced use.</b> Optional SQL to create new tables.
     * Refer to {@link JdbcDialect} source code for examples.
     */
    private String createTableSql;
    /**
     * <b>For advanced use.</b> Optional SQL to create upsert SQLs.
     * Refer to {@link JdbcDialect} source code for examples.
     */
    private String upsertSql;

    /**
     * Connection properties as per
     * <a href="https://github.com/brettwooldridge/HikariCP#gear-configuration-knobs-baby">
     * Hikari's documentation</a>. At a minimum, you need to provide
     * either a "dataSourceClassName" or a "jdbcUrl".
     */
    private Properties properties = new Properties();
}
