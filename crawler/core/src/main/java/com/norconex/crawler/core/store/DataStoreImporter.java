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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonToken;
import com.norconex.crawler.core.Crawler;
import com.norconex.crawler.core.store.impl.SerialUtil;

/**
 * Imports from a previously exported data store.
 */
public final class DataStoreImporter {

    private static final Logger LOG =
            LoggerFactory.getLogger(DataStoreImporter.class);

    private DataStoreImporter() {
    }

    public static void importDataStore(Crawler crawler, Path inFile)
            throws IOException {

        // Export/Import is normally executed in a controlled environment
        // so not susceptible to Zip Bomb attacks.
        try (var zipIn = new ZipInputStream(
                IOUtils.buffer(Files.newInputStream(inFile)))) {
            var zipEntry = zipIn.getNextEntry(); //NOSONAR
            while (zipEntry != null) {
                if (!importStore(crawler, zipIn)) {
                    LOG.debug(
                            "Input file \"{}\" not matching crawler "
                                    + "\"{}\". Skipping.",
                            inFile, crawler.getId());
                }
                zipIn.closeEntry();
                zipEntry = zipIn.getNextEntry(); //NOSONAR
            }
            zipIn.closeEntry();
        }
    }

    private static boolean importStore(
            Crawler crawler, InputStream in) throws IOException {

        var parser = SerialUtil.jsonParser(in);

        String typeStr = null;
        String storeName = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var key = parser.currentName();
            if ("store".equals(key)) {
                storeName = parser.getValueAsString();
            } else if ("type".equals(key)) {
                typeStr = parser.getValueAsString();
            } else if ("records".equals(key)) {
                // check that we first got the store and type.
                if (StringUtils.isAnyBlank(typeStr, storeName)) {
                    LOG.error("Invalid import file encountered.");
                    return false;
                }
                Class<?> type = null;
                try {
                    type = Class.forName(typeStr);
                } catch (ClassNotFoundException e) {
                    throw new IOException(
                            "Could not instantiate type " + typeStr, e);
                }

                LOG.info("Importing \"{}\".", storeName);
                var storeEngine = crawler.getDataStoreEngine();
                try (DataStore<Object> store =
                        storeEngine.openStore(storeName, type)) {
                    var cnt = 0L;
                    parser.nextToken();
                    while (parser.nextToken() != JsonToken.END_ARRAY) {
                        parser.nextToken(); // id:
                        var id = parser.nextTextValue();
                        parser.nextToken(); // object:
                        parser.nextToken(); // { //NOSONAR
                        store.save(id, SerialUtil.fromJson(parser, type));
                        parser.nextToken(); // } //NOSONAR
                        cnt++;
                        logProgress(cnt, false);
                    }
                    logProgress(cnt, true);
                }
            } else {
                parser.nextToken();
            }

        }
        return true;
    }

    private static void logProgress(long cnt, boolean done) {
        if (LOG.isInfoEnabled() && (cnt % 10000 == 0 ^ done)) {
            LOG.info(
                    "{} imported.",
                    NumberFormat.getIntegerInstance().format(cnt));
        }
    }
}
