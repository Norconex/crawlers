/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.data.store.impl.mongo;

import com.norconex.collector.core.data.store.ICrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.mongo.AbstractMongoCrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.mongo.IMongoSerializer;

/**
 * Mongo implementation of {@link ICrawlDataStoreFactory}.
 * <p />
 * XML configuration usage:
 * <p />
 * <pre>
 *  &lt;crawlDataStoreFactory  
 *      class="com.norconex.collector.http.data.store.impl.mongo.MongoCrawlDataStoreFactory"&gt;
 *      &lt;host&gt;(Optional Mongo server hostname. Default to localhost)&lt;/host&gt;
 *      &lt;port&gt;(Optional Mongo port. Default to 27017)&lt;/port&gt;
 *      &lt;dbname&gt;(Optional Mongo database name. Default to crawl id)&lt;/dbname&gt;
 *      &lt;username&gt;(Optional user name)&lt;/username&gt;
 *      &lt;password&gt;(Optional user password)&lt;/password&gt;
 *  &lt;/crawlDataStoreFactory&gt;
 * </pre>
 * <p>
 * If "username" is not provided, no authentication will be attempted. 
 * The "username" must be a valid user that has the "readWrite" role over 
 * the database (set with "dbname").
 * </p>
 * @author Pascal Essiembre
 */
public class MongoCrawlDataStoreFactory 
        extends AbstractMongoCrawlDataStoreFactory {

    @Override
    protected IMongoSerializer createMongoSerializer() {
        return new MongoCrawlDataSerializer();
    }
    
}
