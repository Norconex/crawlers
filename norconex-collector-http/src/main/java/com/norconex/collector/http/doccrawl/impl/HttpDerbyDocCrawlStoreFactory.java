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
package com.norconex.collector.http.doccrawl.impl;

import com.norconex.collector.core.crawler.ICrawlerConfig;
import com.norconex.collector.core.doccrawl.store.IDocCrawlStore;
import com.norconex.collector.core.doccrawl.store.IDocCrawlStoreFactory;
import com.norconex.collector.core.doccrawl.store.impl.derby.DerbyDocCrawlStore;

/**
 * @author Pascal Essiembre
 */
public class HttpDerbyDocCrawlStoreFactory 
        implements IDocCrawlStoreFactory {

    private static final long serialVersionUID = 2288102775288980171L;

    @Override
    public IDocCrawlStore createReferenceStore(
            ICrawlerConfig config, boolean resume) {
        String storeDir = config.getWorkDir().getPath()
                + "/refstore/" + config.getId() + "/";
        return new DerbyDocCrawlStore(storeDir, resume, 
                new HttpDerbySerializer());
    }
    
}
