/* Copyright 2010-2017 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl.jdbc;

import com.norconex.collector.core.data.store.ICrawlDataStore;
import com.norconex.collector.core.data.store.impl.jdbc.BasicJDBCCrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.jdbc.IJDBCSerializer;

/**
 * <p>
 * JDBC implementation of {@link ICrawlDataStore} using H2.
 * </p>
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;crawlDataStoreFactory 
 *          class="com.norconex.collector.http.data.store.impl.jdbc.JDBCCrawlDataStoreFactory"&gt;
 *      &lt;database&gt;[h2|derby]&lt;/database&gt;
 *  &lt;/crawlDataStoreFactory&gt;
 * </pre>
 * 
 * <h4>Usage example:</h4>
 * <p>
 * The following changes the default to use an embedded derby database.
 * </p> 
 * <pre>
 *  &lt;crawlDataStoreFactory 
 *          class="com.norconex.collector.http.data.store.impl.jdbc.JDBCCrawlDataStoreFactory"&gt;
 *      &lt;database&gt;derby&lt;/database&gt;
 *  &lt;/crawlDataStoreFactory&gt;;
 * </pre>
 * @author Pascal Essiembre
 */
public class JDBCCrawlDataStoreFactory 
        extends BasicJDBCCrawlDataStoreFactory {

    public JDBCCrawlDataStoreFactory() {
        super();
    }

    @Override
    protected IJDBCSerializer createJDBCSerializer() {
        return new JDBCCrawlDataSerializer();
    }
}

