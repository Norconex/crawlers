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

import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridCompute;
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

    private final Path storeDir;
    private final MVStore mvstore;
    private final CrawlerContext crawlerContext;
    private final LocalGridCompute gridCompute;
    private final LocalGridStorage gridStorage;

    public LocalGrid(
            Path storeDir, MVStore mvstore, CrawlerContext commandContext) {
        this.storeDir = storeDir;
        this.mvstore = mvstore;
        crawlerContext = commandContext;
        gridCompute = new LocalGridCompute(mvstore, commandContext);
        gridStorage = new LocalGridStorage(mvstore);
    }

    @Override
    public GridCompute compute() {
        return gridCompute;
    }

    @Override
    public GridStorage storage() {
        return gridStorage;
    }

    @Override
    public void close() {
        mvstore.close();
    }
}
