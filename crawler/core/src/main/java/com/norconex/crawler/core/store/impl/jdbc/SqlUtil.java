/* Copyright 2023 Norconex Inc.
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

import org.apache.commons.lang3.StringUtils;

import com.norconex.crawler.core.store.DataStoreException;

final class SqlUtil {

    private SqlUtil() {}

    /**
     * Modifies the value to prevent SQL injection. Spaces are converted
     * to underscores, and unsupported characters are stripped. The supported
     * characters are: alphanumeric, period, and underscore.
     * @param tableName table name
     * @return safe table name
     */
    static String safeTableName(String tableName) {
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
