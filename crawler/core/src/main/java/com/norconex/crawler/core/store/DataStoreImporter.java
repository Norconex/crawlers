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

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.JsonReader;
import com.norconex.crawler.core.crawler.Crawler;
import com.norconex.crawler.core.crawler.CrawlerException;

/**
 * Imports from a previously exported data store.
 */
public final class DataStoreImporter extends CrawlerException {

    private static final long serialVersionUID = 1L;

    private static final Logger LOG =
            LoggerFactory.getLogger(DataStoreImporter.class);

    private DataStoreImporter() {}

    public static void importDataStore(Crawler crawler, Path inFile)
            throws IOException {

        try (var zipIn = new ZipInputStream(
                IOUtils.buffer(Files.newInputStream(inFile)))) {
            var zipEntry = zipIn.getNextEntry();
            while (zipEntry != null) {
                if (!importStore(crawler, zipIn)) {
                    LOG.debug("Input file \"{}\" not matching crawler "
                            + "\"{}\". Skipping.", inFile, crawler.getId());
                }
                zipIn.closeEntry();
                zipEntry = zipIn.getNextEntry();
            }
            zipIn.closeEntry();
        }
    }

    private static boolean importStore(
            Crawler crawler, InputStream in) throws IOException {

        var gson = new Gson();
        var reader = new JsonReader(
                new InputStreamReader(in, StandardCharsets.UTF_8));

        String typeStr = null;
        String crawlerId = null;
        String storeName = null;

        reader.beginObject();

        while(reader.hasNext()) {
            var key = reader.nextName();
            if ("crawler".equals(key)) {
                crawlerId = reader.nextString();
                if (!crawler.getId().equals(crawlerId)) {
                    return false;
                }
            } else if ("store".equals(key)) {
                storeName = reader.nextString();
            } else if ("type".equals(key)) {
                typeStr = reader.nextString();
            } else if ("records".equals(key)) {
                // check if we got crawler first and it matched, else
                // there is something wrong (records should only exist
                // after expected fields.
                if (StringUtils.isAnyBlank(typeStr, crawlerId, storeName)) {
                    LOG.error("Invalid import file encountered.");
                    return false;
                }
                Class<?> type = null;
                try {
                    type = Class.forName(typeStr);
                } catch (JsonIOException | JsonSyntaxException
                        | ClassNotFoundException e) {
                    throw new IOException(
                            "Could not instantiate type " + typeStr, e);
                }
                LOG.info("Importing \"{}\".", storeName);
                var storeEngine = crawler.getDataStoreEngine();
                IDataStore<?> store = storeEngine.openStore(storeName, type);
                reader.beginArray();
                var cnt = 0L;
                while (reader.hasNext()) {
                    reader.beginObject();
                    reader.nextName();
                    var id = reader.nextString();
                    reader.nextName();
                    store.save(id, gson.fromJson(reader, type));
                    reader.endObject();
                    cnt++;
                    logProgress(cnt, false);
                }
                logProgress(cnt, true);
                reader.endArray();
            } else {
                reader.skipValue();
            }
        }
        reader.endObject();
        return true;
    }

    private static void logProgress(long cnt, boolean done) {
        if (LOG.isInfoEnabled() && (cnt % 10000 == 0 ^ done)) {
            LOG.info("{} imported.",
                    NumberFormat.getIntegerInstance().format(cnt));
        }
    }
}
