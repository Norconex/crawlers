/* Copyright 2019-2024 Norconex Inc.
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
package com.norconex.crawler.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.grid.GridSet;
import com.norconex.crawler.core.grid.GridStore;
import com.norconex.crawler.core.store.impl.SerialUtil;

import lombok.extern.slf4j.Slf4j;

/**
 * Exports data stores to a format that can be imported back to the same
 * or different store implementation.
 */
@Slf4j
public final class DataStoreExporter {

    //TODO eliminate client vs server and make server-only method
    // fail for clients?
    //TODO have store() and compute() ?

    private DataStoreExporter() {
    }

    public static Path exportDataStore(
            Crawler crawler, Path exportDir)
            throws IOException {
        var gridServer = crawler.getGrid().storage();
        Files.createDirectories(exportDir);

        var outFile = exportDir.resolve(
                FileUtil.toSafeFileName(crawler.getId() + ".zip"));

        try (var zipOS = new ZipOutputStream(
                IOUtils.buffer(Files.newOutputStream(outFile)), UTF_8)) {

            gridServer.forEachStore(store -> {
                try {
                    zipOS.putNextEntry(new ZipEntry(
                            FileUtil.toSafeFileName(store.getName())
                                    + ".json"));
                    exportStore(store, zipOS);
                    zipOS.flush();
                    zipOS.closeEntry();
                } catch (IOException e) {
                    throw new DataStoreException();
                }
            });
            zipOS.flush();
        }
        return outFile;
    }

    private static void exportStore(
            GridStore<?> store, OutputStream out) throws IOException {

        var writer = SerialUtil.jsonGenerator(out);
        //MAYBE add "nice" option?
        //writer.setIndent(" ");
        var qty = store.size();

        LOG.info("Exporting {} entries from \"{}\".", qty, store.getName());

        var cnt = new MutableLong();
        var lastPercent = new MutableLong();
        writer.writeStartObject();

        // Store metadata
        @SuppressWarnings("rawtypes")
        Class<? extends GridStore> storeType = GridCache.class;
        if (store instanceof GridSet) {
            storeType = GridSet.class;
        } else if (store instanceof GridQueue) {
            storeType = GridQueue.class;
        }
        writer.writeFieldName("store");
        writer.writeStartObject();
        writer.writeStringField("name", store.getName());
        writer.writeStringField("objectType", store.getType().getName());
        writer.writeStringField("storeType", storeType.getName());
        writer.writeEndObject();

        // Store records
        writer.writeFieldName("records");
        writer.writeStartArray();

        store.forEach((id, obj) -> {
            try {
                writer.writeStartObject();
                writer.writeStringField("id", id);
                writer.writePOJOField("object", obj);
                writer.writeEndObject();
                var c = cnt.incrementAndGet();
                var percent = Math.floorDiv(c * 100, qty);
                if (percent != lastPercent.longValue()) {
                    LOG.info(" {}%", percent);
                }
                lastPercent.setValue(percent);
                return true;
            } catch (IOException e) {
                throw new DataStoreException("Could not export " + id, e);
            }
        });

        writer.writeEndArray();
        writer.writeEndObject();
        writer.flush();
    }
}
