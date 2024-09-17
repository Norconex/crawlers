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

import java.net.MalformedURLException;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;

import com.norconex.crawler.core.store.AbstractDataStoreEngineTest;
import com.norconex.crawler.core.store.DataStoreEngine;

import lombok.extern.slf4j.Slf4j;

@Slf4j
class JdbcDataStoreEngineTest extends AbstractDataStoreEngineTest {

    @Override
    protected DataStoreEngine createEngine() {
        try {
            var dbPath = StringUtils.removeStart(
                    getTempDir().toAbsolutePath().toUri().toURL()
                            + "test",
                    "file:");
            if (SystemUtils.IS_OS_WINDOWS) {
                dbPath = StringUtils.removeStart(dbPath, "/");
            }
            var connStr = "jdbc:h2:file:" + dbPath;
            LOG.info("Creating new JDBC data store engine using: {}", connStr);
            var engine = new JdbcDataStoreEngine();
            var cfg = new Properties();
            cfg.setProperty("jdbcUrl", connStr);
            engine.getConfiguration().setProperties(cfg);
            return engine;
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
