/* Copyright 2024 Norconex Inc.
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
package com.norconex.crawler.core.grid.impl.local;

import java.nio.file.Path;

import org.apache.commons.lang3.ObjectUtils;

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridCompute;
import com.norconex.crawler.core.grid.GridServices;
import com.norconex.crawler.core.grid.GridStorage;
import com.norconex.shaded.h2.mvstore.MVStore;

import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * A "local" grid implementation, using the host resources only and using an
 * embedded key-value store database
 * (<a href="https://www.h2database.com/html/mvstore.html">MVStore</a>).
 * This is the default grid implementation and the one recommended when you
 * do not need to run the crawler in a clustered environment.
 */
@EqualsAndHashCode
@ToString
public class LocalGrid implements Grid {

    private MVStore mvstore;
    private Path storeDir;
    private CrawlerContext crawlerContext;
    private LocalGridCompute gridCompute;
    private LocalGridStorage gridStorage;
    private LocalGridServices gridServices;

    public void init(MVStore mvstore, CrawlerContext crawlerContext) {
        this.mvstore = mvstore;
        this.crawlerContext = crawlerContext;
        gridCompute = new LocalGridCompute(mvstore, crawlerContext);
        gridStorage = new LocalGridStorage(mvstore);
        gridServices = new LocalGridServices(crawlerContext);
    }

    @Override
    public GridServices services() {
        ensureInit();
        return gridServices;
    }

    @Override
    public GridCompute compute() {
        ensureInit();
        return gridCompute;
    }

    @Override
    public GridStorage storage() {
        ensureInit();
        return gridStorage;
    }

    @Override
    public void close() {
        mvstore.close();
        gridCompute = null;
        gridStorage = null;
        storeDir = null;
        mvstore = null;
        if (crawlerContext != null) {
            crawlerContext.close();
        }
        crawlerContext = null;
    }

    private void ensureInit() {
        if (ObjectUtils.anyNull(gridCompute, gridStorage, gridServices)) {
            throw new IllegalStateException("LocalGrid not initialized.");
        }
    }
}
