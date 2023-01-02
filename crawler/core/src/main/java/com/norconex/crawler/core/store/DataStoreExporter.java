/* Copyright 2019-2022 Norconex Inc.
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
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerException;

/**
 * Exports data stores to a format that can be imported back to the same
 * or different store implementation.
 */
public final class DataStoreExporter extends CrawlerException {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(DataStoreExporter.class);

    private DataStoreExporter() {
    }

    public static Path exportDataStore(Crawler crawler, Path exportDir)
            throws IOException {
        var storeEngine = crawler.getDataStoreEngine();
//        try {
            Files.createDirectories(exportDir);
//        } catch (IOException e) {
//            throw new CollectorException("Could not create export directory: "
//                    + exportDir, e);
//        }

        var outFile = exportDir.resolve(
                FileUtil.toSafeFileName(crawler.getId() + ".zip"));

        try (var zipOS = new ZipOutputStream(
                IOUtils.buffer(Files.newOutputStream(outFile)), UTF_8)) {

            for (String name : storeEngine.getStoreNames()) {
                var type = storeEngine.getStoreType(name);
                if (type.isPresent()) {
                    zipOS.putNextEntry(new ZipEntry(
                            FileUtil.toSafeFileName(name) + ".json"));
                    try (IDataStore<?> store =
                            storeEngine.openStore(name, type.get())) {
                        exportStore(crawler, store, zipOS, type.get());
                    }
                    zipOS.flush();
                    zipOS.closeEntry();

                } else {
                    LOG.error("Could not obtain store {}", name);
                }
            }
            zipOS.flush();
//        } catch (IOException e) {
//            throw new CollectorException(
//                    "Could not export data store to " + outFile, e);
        }
        return outFile;
    }
    private static void exportStore(Crawler crawler, IDataStore<?> store,
            OutputStream out, Class<?> type)
                    throws IOException {
        var gson = new Gson();
        var writer = new JsonWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8));
        //TODO add "nice" option?
        //writer.setIndent(" ");
        var qty = store.count();

        LOG.info("Exporting {} entries from \"{}\".", qty, store.getName());

        var cnt = new MutableLong();
        var lastPercent = new MutableLong();
        writer.beginObject();
        writer.name("collector").value(crawler.getCrawlSession().getId());
        writer.name("crawler").value(crawler.getId());
        writer.name("store").value(store.getName());
        writer.name("type").value(type.getName());
        writer.name("records");
        writer.beginArray();

        store.forEach((id, obj) -> {
            try {
                writer.beginObject();
                writer.name("id").value(id);
                writer.name("object");
                gson.toJson(obj, type, writer);
                writer.endObject();
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

        writer.endArray();
        writer.endObject();
        writer.flush();
    }
}
