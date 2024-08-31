/* Copyright 2020-2024 Norconex Inc.
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
package com.norconex.crawler.core.store.impl.mvstore;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.h2.mvstore.MVStoreException;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.unit.DataUnit;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.store.DataStore;
import com.norconex.crawler.core.store.DataStoreEngine;
import com.norconex.crawler.core.store.DataStoreException;

import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
public class MvStoreDataStoreEngine
        implements DataStoreEngine, Configurable<MvStoreDataStoreEngineConfig> {

    private static final String STORE_TYPES_KEY =
            MvStoreDataStoreEngine.class.getSimpleName() + "--storetypes";

    @JsonProperty(Configurable.PROPERTY)
    private final MvStoreDataStoreEngineConfig cfg =
            new MvStoreDataStoreEngineConfig();

    private MVStore mvstore;
    private Path engineDir;
    private MVMap<String, Class<?>> storeTypes;

    @Override
    public MvStoreDataStoreEngineConfig getConfiguration() {
        return cfg;
    }

    @Override
    public void init(Crawler crawler) {

        var builder = new MVStore.Builder();
        if (cfg.getPageSplitSize() != null) {
            //MVStore expects it as bytes
            builder.pageSplitSize(asInt(cfg.getPageSplitSize()));
        }
        if (Integer.valueOf(1).equals(cfg.getCompress())) {
            builder.compress();
        }
        if (Integer.valueOf(2).equals(cfg.getCompress())) {
            builder.compressHigh();
        }
        if (cfg.getCacheConcurrency() != null) {
            builder.cacheConcurrency(cfg.getCacheConcurrency());
        }
        if (cfg.getCacheSize() != null) {
            //MVStore expects it as megabytes
            builder.cacheSize(
                    DataUnit.B.to(cfg.getCacheSize(), DataUnit.MB).intValue());
        }
        if (cfg.getAutoCompactFillRate() != null) {
            builder.autoCompactFillRate(cfg.getAutoCompactFillRate());
        }
        if (cfg.getAutoCommitBufferSize() != null) {
            //MVStore expects it as kilobytes
            builder.autoCommitBufferSize(
                    DataUnit.B.to(
                            cfg.getAutoCommitBufferSize(), DataUnit.KB)
                            .intValue());
        }
        if (Long.valueOf(0).equals(cfg.getAutoCommitDelay())) {
            builder.autoCommitDisabled();
        }

        if (cfg.isEphemeral()) {
            builder.fileName(null);
        } else {
            engineDir = crawler.getWorkDir().resolve("datastore");
            try {
                FileUtils.forceMkdir(engineDir.toFile());
            } catch (IOException e) {
                throw new DataStoreException(
                        "Cannot create data store engine directory: "
                                + engineDir,
                        e);
            }
            builder.fileName(
                    engineDir.resolve("mvstore").toAbsolutePath().toString());
        }

        try {
            mvstore = builder.open();
        } catch (MVStoreException e) {
            LOG.warn(
                    """
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

        if (cfg.getAutoCommitDelay() != null) {
            //MVStore expects it as milliseconds
            mvstore.setAutoCommitDelay(cfg.getAutoCommitDelay().intValue());
        }

        storeTypes = mvstore.openMap(STORE_TYPES_KEY);

        mvstore.commit();
    }

    private Integer asInt(Long l) {
        if (l == null) {
            return null;
        }
        return l.intValue();
    }

    @Override
    public boolean clean() {
        var names = getStoreNames();
        var hadStores = false;
        if (!names.isEmpty()) {
            hadStores = true;
            names.stream().forEach(this::dropStore);
        }
        dropStore(STORE_TYPES_KEY);
        var dirToDelete = engineDir.toFile();
        try {
            FileUtils.deleteDirectory(dirToDelete);
        } catch (IOException e) {
            throw new DataStoreException(
                    "Could not delete data store directory.", e);
        }
        return hadStores;
    }

    @Override
    public synchronized void close() {
        LOG.info("Closing data store engine...");
        if (mvstore != null && !mvstore.isClosed()) {
            LOG.info("Compacting data store...");
            mvstore.commit();
            //TODO method dropped from MVStore. Any replacemetn?
            //mvstore.compactMoveChunks();
            mvstore.close();
        }
        mvstore = null;
        engineDir = null;
        LOG.info("Data store engine closed.");
    }

    @Override
    public synchronized <T> DataStore<T> openStore(
            String name, Class<? extends T> type) {
        storeTypes.put(name, type);
        return new MvStoreDataStore<>(mvstore, name, type);
    }

    @Override
    public synchronized boolean dropStore(String name) {
        if (mvstore.hasMap(name)) {
            mvstore.removeMap(name);
            if (STORE_TYPES_KEY.equals(name)) {
                storeTypes = null;
            } else {
                storeTypes.remove(name);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean renameStore(DataStore<?> store, String newName) {
        MvStoreDataStore<?> mvDateStore = (MvStoreDataStore<?>) store;
        var hadMap = false;
        if (mvstore.hasMap(newName)) {
            hadMap = true;
        }
        storeTypes.put(newName, storeTypes.remove(mvDateStore.rename(newName)));
        return hadMap;
    }

    @Override
    @JsonIgnore
    public Set<String> getStoreNames() {
        // a fresh map instance is returned, so safe to remove entry.
        var names = mvstore.getMapNames();
        names.remove(STORE_TYPES_KEY);
        return names;
    }

    @Override
    @JsonIgnore
    public Optional<Class<?>> getStoreType(String name) {
        return Optional.ofNullable(storeTypes.get(name));
    }
    //
    //    @Override
    //    public void loadFromXML(XML xml) {
    //        cfg.setPageSplitSize(
    //                xml.getDataSize(Fields.pageSplitSize, cfg.getPageSplitSize()));
    //        cfg.setCompress(xml.getInteger(Fields.compress, cfg.getCompress()));
    //        cfg.setCacheConcurrency(xml.getInteger(
    //                Fields.cacheConcurrency, cfg.getCacheConcurrency()));
    //        cfg.setCacheSize(xml.getDataSize(Fields.cacheSize, cfg.getCacheSize()));
    //        cfg.setAutoCompactFillRate(xml.getInteger(
    //                Fields.autoCompactFillRate, cfg.getAutoCompactFillRate()));
    //        cfg.setAutoCommitBufferSize(xml.getDataSize(
    //                Fields.autoCommitBufferSize, cfg.getAutoCommitBufferSize()));
    //        cfg.setAutoCommitDelay(xml.getDurationMillis(
    //                Fields.autoCommitDelay, cfg.getAutoCommitDelay()));
    //        cfg.setEphemeral(xml.getBoolean(Fields.ephemeral, cfg.isEphemeral()));
    //    }
    //
    //    @Override
    //    public void saveToXML(XML xml) {
    //        xml.addElement(Fields.pageSplitSize, cfg.getPageSplitSize());
    //        xml.addElement(Fields.compress, cfg.getCompress());
    //        xml.addElement(Fields.cacheConcurrency, cfg.getCacheConcurrency());
    //        xml.addElement(Fields.cacheSize, cfg.getCacheSize());
    //        xml.addElement(Fields.autoCompactFillRate, cfg.getAutoCompactFillRate());
    //        xml.addElement(Fields.autoCommitBufferSize, cfg.getAutoCommitBufferSize());
    //        xml.addElement(Fields.autoCommitDelay, cfg.getAutoCommitDelay());
    //        xml.addElement(Fields.ephemeral, cfg.isEphemeral());
    //    }
}
