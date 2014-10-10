/* Copyright 2014 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.data.store.impl.jdbc;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.data.store.impl.jdbc.AbstractJDBCDataStoreFactory;
import com.norconex.collector.core.data.store.impl.jdbc.IJDBCSerializer;
import com.norconex.collector.core.data.store.impl.jdbc.JDBCCrawlDataStore.Database;

/**
 * JDBC implementation of {@link ICrawlDataStore}.  Defaults to Derby 
 * database.
 * <p />
 * XML configuration usage:
 * <p />
 * <pre>
 *  &lt;crawlDataStoreFactory 
 *          class="com.norconex.collector.http.data.store.impl.jdbc.JDBCCrawlDataStoreFactory"&gt;
 *      &lt;database&gt;[h2|derby]&lt;/database&gt;
 *  &lt;/crawlDataStoreFactory&gt;
 * </pre>
 *
 * @author Pascal Essiembre
 */
public class JDBCCrawlDataStoreFactory 
        extends AbstractJDBCDataStoreFactory {

    public JDBCCrawlDataStoreFactory() {
        super();
    }
    public JDBCCrawlDataStoreFactory(Database database) {
        super(database);
    }

    @Override
    protected IJDBCSerializer createJDBCSerializer() {
        return new JDBCCrawlDataSerializer();
    }
}

