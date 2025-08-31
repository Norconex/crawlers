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
package com.norconex.crawler.core2.cmd.storeimport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.norconex.crawler.core.cluster.Cache;
import com.norconex.crawler.core.cluster.CacheException;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core2.cmd.Command;
import com.norconex.crawler.core2.event.CrawlerEvent;
import com.norconex.crawler.core2.util.SerialUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StoreImportCommand implements Command {

    private final Path inFile;

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();

        Thread.currentThread().setName(ctx.getId() + "/STORE_IMPORT");
        session.fire(CrawlerEvent.CRAWLER_STORE_IMPORT_BEGIN, this);

        //Export/Import are meant to run on a single node only. We ensure
        // this by checking if we are the coordinator.

        try {
            importAllStores(session);
        } catch (Exception e) {
            throw new CacheException("Could not import file: " + inFile, e);
        }

        //TODO migrate to pipeline
        //        try {
        //            session.getCluster().getTaskManager()
        //                    .runOnOneSync("storeImportTask", sess -> {
        //                        try {
        //                            importAllStores(session);
        //                        } catch (ClassNotFoundException
        //                                | IOException e) {
        //                            throw new CrawlerException(e);
        //                        }
        //                        return null;
        //                    });
        //        } catch (Exception e) {
        //            throw new CrawlerException("Could not import file: " + inFile, e);
        //        }
        session.fire(CrawlerEvent.CRAWLER_STORE_IMPORT_END, this);
    }

    private void importAllStores(CrawlSession session)
            throws IOException, ClassNotFoundException {
        // Export/Import is normally executed in a controlled environment
        // so not susceptible to Zip Bomb attacks.
        try (var zipIn = new ZipInputStream(
                IOUtils.buffer(Files.newInputStream(inFile)))) {
            var zipEntry = zipIn.getNextEntry(); //NOSONAR
            while (zipEntry != null) {
                if (!importOneStore(session, zipIn)) {
                    LOG.debug("Input file \"{}\" not matching crawler "
                            + "\"{}\". Skipping.",
                            inFile, session.getCrawlerId());
                }
                zipIn.closeEntry();
                zipEntry = zipIn.getNextEntry(); //NOSONAR
            }
            zipIn.closeEntry();
        }
    }

    private boolean importOneStore(
            CrawlSession session, InputStream in)
            throws IOException, ClassNotFoundException {

        var cacheManager = session.getCluster().getCacheManager();

        var parser = SerialUtil.jsonParser(in);

        var objectClass = Object.class;// null;
        String storeName = null;
        //        Class<? extends GridStore<?>> storeSuperClass = null;

        parser.nextToken();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var key = parser.currentName();
            if ("crawler".equals(key)) {
                parser.nextTextValue();
            } else if ("store".equals(key)) {
                storeName = parser.nextTextValue();
                //            } else if ("storeType".equals(key)) {
                //                storeSuperClass = (Class<? extends GridStore<?>>) Class.forName(
                //                        parser.nextTextValue());
                //            } else if ("objectType".equals(key)) {
                //                objectClass = Class.forName(parser.nextTextValue());
            } else if ("records".equals(key)) {
                // check if we got crawler first and it matched, else
                // there is something wrong (records should only exist
                // after expected fields.
                //                if (StringUtils.isAnyBlank(crawlerId, storeName)
                //                        || ObjectUtils.anyNull(objectClass, storeSuperClass)) {
                //                    LOG.error("Invalid import file encountered.");
                //                    return false;
                //                }

                LOG.info("Importing \"{}\".", storeName);

                Cache<Object> cache =
                        cacheManager.getCache(storeName, Object.class);
                //                GridStore<?> store = concreteStore(
                //                        cacheManager, storeSuperClass, storeName, objectClass);

                var cnt = 0L;
                parser.nextToken();
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    loadRecord(cache, parser, objectClass);
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

    private <T> void loadRecord(
            Cache<T> cache, JsonParser parser, Class<T> objectClass)
            throws IOException {
        parser.nextToken(); // id:
        var id = parser.nextTextValue();
        parser.nextToken(); // object:
        parser.nextToken(); // { //NOSONAR
        var value = SerialUtil.fromJson(parser, objectClass);
        //        if (cache instanceof GridMap cache) { //NOSONAR
        cache.put(id, value);
        //        }
        parser.nextToken(); // } //NOSONAR
    }

    //    GridStore<?> concreteStore(
    //            CacheManager cacheManager,
    //            Class<?> storeSuperType,
    //            String storeName,
    //            Class<?> objectType) {
    //        if (storeSuperType.equals(GridQueue.class)) {
    //            return cacheManager.getQueue(storeName, objectType);
    //        }
    //        if (storeSuperType.equals(GridSet.class)) {
    //            return cacheManager.getSet(storeName);
    //        }
    //        return cacheManager.getMap(storeName, objectType);
    //    }

    private static void logProgress(long cnt, boolean done) {
        if (LOG.isInfoEnabled() && (cnt % 10000 == 0 ^ done)) {
            LOG.info("{} imported.",
                    NumberFormat.getIntegerInstance().format(cnt));
        }
    }

}
