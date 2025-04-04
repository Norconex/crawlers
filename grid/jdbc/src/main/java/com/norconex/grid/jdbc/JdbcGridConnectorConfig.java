/* Copyright 2024-2025 Norconex Inc.
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
package com.norconex.grid.jdbc;

import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.commons.lang.map.Properties;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * JDBC grid connector configuration.
 * </p>
 */
@Data
@Accessors(chain = true)
public class JdbcGridConnectorConfig {

    /**
     * Name for the grid you are connecting to.
     */
    private String gridName = "jdbc-grid";

    private final Properties datasource = new Properties();

    private String varcharType;
    private String bigIntType;
    private String textType;

    public void setDatasource(Properties datasource) {
        CollectionUtil.setAll(this.datasource, datasource);
    }

}
