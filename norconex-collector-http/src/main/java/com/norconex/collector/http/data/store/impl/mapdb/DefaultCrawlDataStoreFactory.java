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
package com.norconex.collector.http.data.store.impl.mapdb;

import com.norconex.collector.core.data.store.ICrawlDataStoreFactory;
import com.norconex.collector.core.data.store.impl.mapdb.MapDBCrawlDataStoreFactory;

/**
 * Default {@link ICrawlDataStoreFactory} implementation. 
 * This class is a straight extension of
 * {@link MapDBCrawlDataStoreFactory} and as such, uses MapDB for its 
 * implementation
 * <p />
 * XML configuration usage (not required since default):
 * <p />
 * <pre>
 *  &lt;crawlDataStoreFactory  
 *      class="com.norconex.collector.http.data.store.impl.mapdb.DefaultCrawlDataStoreFactory" /&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultCrawlDataStoreFactory 
        extends MapDBCrawlDataStoreFactory {

    private static final long serialVersionUID = 370632354864351545L;

}
