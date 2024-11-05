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

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;

import com.norconex.commons.lang.ClassUtil;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.crawler.core.CrawlerConfig;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerSpecProvider;
import com.norconex.crawler.core.grid.Grid;
import com.norconex.crawler.core.grid.GridConnector;
import com.norconex.crawler.core.grid.GridException;
import com.norconex.crawler.core.util.ConfigUtil;
import com.norconex.shaded.h2.mvstore.MVStore;
import com.norconex.shaded.h2.mvstore.MVStoreException;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
public class LocalGridConnector
        implements GridConnector, Configurable<LocalGridConnectorConfig> {

    @Getter
    private final LocalGridConnectorConfig configuration =
            new LocalGridConnectorConfig();

    @SuppressWarnings("resource")
    @Override
    public Grid connect(
            Class<? extends CrawlerSpecProvider> specProviderClass,
            CrawlerConfig crawlerConfig) {
        var builder = new MVStore.Builder();
        if (configuration.getPageSplitSize() != null) {
            //MVStore expects it as bytes
            builder.pageSplitSize(asInt(configuration.getPageSplitSize()));
        }
        if (Integer.valueOf(1).equals(configuration.getCompress())) {
            builder.compress();
        }
        if (Integer.valueOf(2).equals(configuration.getCompress())) {
            builder.compressHigh();
        }
        if (configuration.getCacheConcurrency() != null) {
            builder.cacheConcurrency(configuration.getCacheConcurrency());
        }
        if (configuration.getCacheSize() != null) {
            //MVStore expects it as megabytes
            builder.cacheSize(
                    DataUnit.B.to(configuration.getCacheSize(), DataUnit.MB)
                            .intValue());
        }
        if (configuration.getAutoCompactFillRate() != null) {
            builder.autoCompactFillRate(configuration.getAutoCompactFillRate());
        }
        if (configuration.getAutoCommitBufferSize() != null) {
            //MVStore expects it as kilobytes
            builder.autoCommitBufferSize(
                    DataUnit.B.to(
                            configuration.getAutoCommitBufferSize(),
                            DataUnit.KB)
                            .intValue());
        }
        if (Long.valueOf(0).equals(configuration.getAutoCommitDelay())) {
            builder.autoCommitDisabled();
        }

        var spec = ClassUtil.newInstance(specProviderClass).get();
        var workDir = ConfigUtil.resolveWorkDir(crawlerConfig);

        Path storeDir = null;
        if (configuration.isEphemeral()) {
            builder.fileName(null);
        } else {
            storeDir = workDir.resolve("datastore");
            try {
                FileUtils.forceMkdir(storeDir.toFile());
            } catch (IOException e) {
                throw new GridException(
                        "Cannot create store directory: " + storeDir, e);
            }
            builder.fileName(
                    storeDir.resolve("mvstore").toAbsolutePath().toString());
        }

        MVStore mvstore;
        try {
            mvstore = builder.open();
        } catch (MVStoreException e) {
            LOG.warn("""
                    An exception occurred while trying to open the store engine.\s\
                    This could happen due to an abnormal shutdown on a previous\s\
                    execution of the crawler. An attempt will be made to recover.\s\
                    It is advised to back-up the store engine if you want to\s\
                    preserve the crawl history.""",
                    e);
            builder.recoveryMode();
            mvstore = builder.open();
            LOG.warn("Store engine recovery appears to be successful.");
        }

        if (configuration.getAutoCommitDelay() != null) {
            //MVStore expects it as milliseconds
            mvstore.setAutoCommitDelay(
                    configuration.getAutoCommitDelay().intValue());
        }

        //        storeTypes = mvstore.openMap(STORE_TYPES_KEY);

        mvstore.commit();

        //TODO fix circular reference where CrawlerContext
        // initializes the Grid and the grid initializes the CrawlerContext
        // which depends on CrawlerContext to be initialized.
        // Do some lazy loading.

        //        var taskContext = new CrawlerContext(crawlerContext);
        var grid = new LocalGrid();
        var crawlerContext = new CrawlerContext(spec, crawlerConfig, grid);
        grid.init(storeDir, mvstore, crawlerContext);
        crawlerContext.init(); // closed by LocalGrid#close()
        return grid;
    }

    private Integer asInt(Long l) {
        if (l == null) {
            return null;
        }
        return l.intValue();
    }
}