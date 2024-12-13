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
package com.norconex.crawler.core.cmd.storeimport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridCache;
import com.norconex.crawler.core.grid.GridQueue;
import com.norconex.crawler.core.grid.GridSet;
import com.norconex.crawler.core.grid.GridStorage;
import com.norconex.crawler.core.grid.GridStore;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StoreImportCommand implements Command {

    private final Path inFile;

    @Override
    public void execute(CrawlerContext ctx) {
        Thread.currentThread().setName(ctx.getId() + "/STORE_IMPORT");
        ctx.fire(CrawlerEvent.CRAWLER_STORE_IMPORT_BEGIN);
        try {
            ConcurrentUtil.block(ctx.getGrid().compute().runLocalOnce(
                    StoreImportCommand.class.getSimpleName(), () -> {
                        importAllStores(ctx);
                        return null;
                    }));
        } catch (Exception e) {
            throw new CrawlerException("Could not import file: " + inFile, e);
        }
        ctx.fire(CrawlerEvent.CRAWLER_STORE_IMPORT_END);
    }

    private void importAllStores(CrawlerContext crawlerContext)
            throws IOException, ClassNotFoundException {
        // Export/Import is normally executed in a controlled environment
        // so not susceptible to Zip Bomb attacks.
        try (var zipIn = new ZipInputStream(
                IOUtils.buffer(Files.newInputStream(inFile)))) {
            var zipEntry = zipIn.getNextEntry(); //NOSONAR
            while (zipEntry != null) {
                if (!importOneStore(crawlerContext, zipIn)) {
                    LOG.debug("Input file \"{}\" not matching crawler "
                            + "\"{}\". Skipping.",
                            inFile, crawlerContext.getId());
                }
                zipIn.closeEntry();
                zipEntry = zipIn.getNextEntry(); //NOSONAR
            }
            zipIn.closeEntry();
        }
    }

    @SuppressWarnings("unchecked")
    private boolean importOneStore(
            CrawlerContext crawlerContext, InputStream in)
            throws IOException, ClassNotFoundException {

        var parser = SerialUtil.jsonParser(in);

        Class<?> objectClass = null;
        String crawlerId = null;
        String storeName = null;
        Class<? extends GridStore<?>> storeSuperClass = null;

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var key = parser.currentName();
            if ("store".equals(key)) {
                storeName = parser.nextTextValue();
            } else if ("crawler".equals(key)) {
                crawlerId = parser.nextTextValue();
            } else if ("storeType".equals(key)) {
                storeSuperClass = (Class<? extends GridStore<?>>) Class.forName(
                        parser.nextTextValue());
            } else if ("objectType".equals(key)) {
                objectClass = Class.forName(parser.nextTextValue());
            } else if ("records".equals(key)) {
                // check if we got crawler first and it matched, else
                // there is something wrong (records should only exist
                // after expected fields.
                if (StringUtils.isAnyBlank(crawlerId, storeName)
                        || ObjectUtils.anyNull(objectClass, storeSuperClass)) {
                    LOG.error("Invalid import file encountered.");
                    return false;
                }

                LOG.info("Importing \"{}\".", storeName);
                var storage = crawlerContext.getGrid().storage();

                GridStore<?> store = concreteStore(
                        storage, storeSuperClass, storeName, objectClass);

                var cnt = 0L;
                parser.nextToken();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    loadRecord(store, parser, objectClass);
                    cnt++;
                    logProgress(cnt, false);
                }
                logProgress(cnt, true);
            } else {
                parser.nextValue();
            }

        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void loadRecord(
            GridStore<?> store, JsonParser parser, Class<?> objectClass)
            throws IOException {
        parser.nextToken(); // id:
        var id = parser.nextTextValue();
        var value = SerialUtil.fromJson(parser, objectClass);
        parser.nextToken(); // object:
        parser.nextToken(); // { //NOSONAR
        if (store instanceof GridCache cache) { //NOSONAR
            cache.put(id, value);
        } else if (store instanceof GridSet set) { //NOSONAR
            set.add(objectClass);
        } else if (store instanceof GridQueue queue) { //NOSONAR
            queue.put(id, value);
        }
        parser.nextToken(); // } //NOSONAR
    }

    GridStore<?> concreteStore(
            GridStorage storage,
            Class<?> storeSuperType,
            String storeName,
            Class<?> objectType) {
        if (storeSuperType.equals(GridQueue.class)) {
            return storage.getQueue(storeName, objectType);
        }
        if (storeSuperType.equals(GridSet.class)) {
            return storage.getSet(storeName, objectType);
        }
        return storage.getCache(storeName, objectType);
    }

    private static void logProgress(long cnt, boolean done) {
        if (LOG.isInfoEnabled() && (cnt % 10000 == 0 ^ done)) {
            LOG.info("{} imported.",
                    NumberFormat.getIntegerInstance().format(cnt));
        }
    }

}