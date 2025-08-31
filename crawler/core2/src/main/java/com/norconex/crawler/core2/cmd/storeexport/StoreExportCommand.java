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
package com.norconex.crawler.core2.cmd.storeexport;

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
import com.norconex.crawler.core.CrawlerException;
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
public class StoreExportCommand implements Command {

    private final Path exportDir;
    private final boolean pretty;

    //TODO have wrapper StoppableRunnable that when invoked,
    // set stopRequested on context?  Or do it higher up on context?

    @Override
    public void execute(CrawlSession session) {
        var ctx = session.getCrawlContext();

        Thread.currentThread().setName(ctx.getId() + "/STORE_EXPORT");
        session.fire(CrawlerEvent.CRAWLER_STORE_EXPORT_BEGIN, this);

        //Export/Import are meant to run on a single node only. We ensure
        // this by checking if we are the coordinator.

        if (session.getCluster().getLocalNode().isCoordinator()) {
            try {
                exportAllStores(session);
            } catch (Exception e) {
                throw new CacheException(
                        "A problem occured while exporting crawler caches.", e);
            }
        } else {
            LOG.warn("""
                Exporting can only be performed on a single node. \
                Another node started the export process so this one \
                will ignore the request.""");
        }

        //TODO migrate to pipeline
        //        try {
        //            session.getCluster().getTaskManager()
        //                    .runOnOneSync("storeImportTask", sess -> {
        //                        try {
        //                            exportAllStores(sess);
        //                        } catch (IOException e) {
        //                            throw new CrawlerException(e);
        //                        }
        //                        return null;
        //                    });
        //                } catch (Exception e) {
        //                    throw new CrawlerException(
        //                            "A problem occured while exporting crawler storage.", e);
        //                }
        session.fire(CrawlerEvent.CRAWLER_STORE_EXPORT_END, this);
    }

    private void exportAllStores(CrawlSession session)
            throws IOException {
        var cacheManager = session.getCluster().getCacheManager();
        Files.createDirectories(exportDir);

        var outFile = exportDir.resolve(
                FileUtil.toSafeFileName(session.getCrawlerId() + ".zip"));
        LOG.info("Exporting crawler storage to file: {}", outFile);

        try (var zipOS = new ZipOutputStream(
                IOUtils.buffer(Files.newOutputStream(outFile)), UTF_8)) {
            cacheManager.forEach((name, cache) -> {

                //                                var type = cache.getType();
                try {
                    zipOS.putNextEntry(new ZipEntry(
                            FileUtil.toSafeFileName(name) + ".json"));
                    exportOneStore(session, name, cache, zipOS);//, type);
                    zipOS.flush();
                    zipOS.closeEntry();
                } catch (IOException e) {
                    throw new CrawlerException(
                            "Could not export store: " + name, e);
                }
            });
            zipOS.flush();
        }
        LOG.info("Storage exported to file: {}", outFile);
    }

    private void exportOneStore(
            CrawlSession session,
            String name,
            Cache<?> cache,
            OutputStream out
    //            ,
    //            Class<?> type
    ) throws IOException {

        var writer = SerialUtil.jsonGenerator(out);
        if (pretty) {
            writer.useDefaultPrettyPrinter();
        }
        var qty = cache.size();

        LOG.info("Exporting {} entries from \"{}\".", qty, name);

        var cnt = new MutableLong();
        var lastPercent = new MutableLong();
        writer.writeStartObject();
        writer.writeStringField("crawler", session.getCrawlerId());
        writer.writeStringField("store", name);
        //        writer.writeStringField("storeType", storeSuperClassName(cache));
        //        writer.writeStringField("objectType", type.getName());
        writer.writeFieldName("records");
        writer.writeStartArray();

        cache.forEach((id, obj) -> {
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
            } catch (IOException e) {
                throw new CrawlerException("Could not export " + id, e);
            }
        });

        writer.writeEndArray();
        writer.writeEndObject();
        writer.flush();
    }

    //    private String storeSuperClassName(GridStore<?> store) {
    //        var concreteClass = store.getClass();
    //        if (GridQueue.class.isAssignableFrom(concreteClass)) {
    //            return GridQueue.class.getName();
    //        }
    //        if (GridSet.class.isAssignableFrom(concreteClass)) {
    //            return GridSet.class.getName();
    //        }
    //        return GridMap.class.getName();
    //    }
}
