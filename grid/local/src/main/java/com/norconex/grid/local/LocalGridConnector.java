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
package com.norconex.grid.local;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.grid.core.Grid;
import com.norconex.grid.core.GridConnector;
import com.norconex.grid.core.GridException;

import lombok.AccessLevel;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
@NoArgsConstructor
public class LocalGridConnector
        implements GridConnector, Configurable<LocalGridConnectorConfig> {

    public static final String DEFAULT_GRID_NAME = "local-grid";
    private static final String STORE_DIR_NAME = "localStore";

    // Grid name not configurable, available via package scope for testing.
    @Getter(value = AccessLevel.PACKAGE)
    private String gridName = DEFAULT_GRID_NAME;

    LocalGridConnector(@NonNull String gridName) {
        this.gridName = gridName;
    }

    @Getter
    private final LocalGridConnectorConfig configuration =
            new LocalGridConnectorConfig();

    @Override
    public Grid connect(Path workDir) {
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

        Path storeDir = null;
        if (configuration.isEphemeral()) {
            builder.fileName(null);
        } else {
            storeDir = workDir.resolve(STORE_DIR_NAME);
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
                An exception occurred while trying to open the store engine.

                The message is:

                   %s

                It is likely that:

                1. There is another process using it, which is not supported
                   when using the "local grid" implementation.

                2. Could happen due to an abnormal shutdown on a previous
                   execution of the crawler. An attempt will be made to recover.
                   It is advised to back-up the store engine if you want to
                   preserve the crawl history.
                """.formatted(e.getLocalizedMessage()),
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
        mvstore.commit();

        return new LocalGrid(mvstore, gridName);
    }

    @Override
    public void shutdownGrid(Path workDir) {
        LocalGridStopHandler.requestStop(workDir.resolve(STORE_DIR_NAME));
    }

    private Integer asInt(Long l) {
        if (l == null) {
            return null;
        }
        return l.intValue();
    }
}
