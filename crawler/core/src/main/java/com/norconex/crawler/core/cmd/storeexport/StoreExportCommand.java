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
package com.norconex.crawler.core.cmd.storeexport;

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
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlContext;
import com.norconex.grid.core.compute.GridTaskBuilder;
import com.norconex.grid.core.storage.GridMap;
import com.norconex.grid.core.storage.GridQueue;
import com.norconex.grid.core.storage.GridSet;
import com.norconex.grid.core.storage.GridStore;
import com.norconex.grid.core.util.SerialUtil;

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
    public void execute(CrawlContext ctx) {
        Thread.currentThread().setName(ctx.getId() + "/STORE_EXPORT");
        ctx.fire(CrawlerEvent.CRAWLER_STORE_EXPORT_BEGIN);
        try {
            ctx.getGrid().getCompute()
                    .executeTask(GridTaskBuilder.create("storeExportTask")
                            .singleNode()
                            .processor(grid -> {
                                try {
                                    exportAllStores(ctx);
                                } catch (IOException e) {
                                    throw new CrawlerException(e);
                                }
                            })
                            .build());
        } catch (Exception e) {
            throw new CrawlerException(
                    "A problem occured while exporting crawler storage.", e);
        }
        ctx.fire(CrawlerEvent.CRAWLER_STORE_EXPORT_END);
    }

    private void exportAllStores(CrawlContext crawlContext)
            throws IOException {
        var storage = crawlContext.getGrid().getStorage();
        Files.createDirectories(exportDir);

        var outFile = exportDir.resolve(
                FileUtil.toSafeFileName(crawlContext.getId() + ".zip"));
        LOG.info("Exporting crawler storage to file: {}", outFile);

        try (var zipOS = new ZipOutputStream(
                IOUtils.buffer(Files.newOutputStream(outFile)), UTF_8)) {
            storage.forEachStore(store -> {
                var name = store.getName();
                var type = store.getType();
                try {
                    zipOS.putNextEntry(new ZipEntry(
                            FileUtil.toSafeFileName(name) + ".json"));
                    exportOneStore(crawlContext, store, zipOS, type);
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
            CrawlContext crawlContext,
            GridStore<?> store,
            OutputStream out,
            Class<?> type) throws IOException {

        var writer = SerialUtil.jsonGenerator(out);
        if (pretty) {
            writer.useDefaultPrettyPrinter();
        }
        var qty = store.size();

        LOG.info("Exporting {} entries from \"{}\".", qty, store.getName());

        var cnt = new MutableLong();
        var lastPercent = new MutableLong();
        writer.writeStartObject();
        writer.writeStringField("crawler", crawlContext.getId());
        writer.writeStringField("store", store.getName());
        writer.writeStringField("storeType", storeSuperClassName(store));
        writer.writeStringField("objectType", type.getName());
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
                throw new CrawlerException("Could not export " + id, e);
            }
        });

        writer.writeEndArray();
        writer.writeEndObject();
        writer.flush();
    }

    private String storeSuperClassName(GridStore<?> store) {
        var concreteClass = store.getClass();
        if (GridQueue.class.isAssignableFrom(concreteClass)) {
            return GridQueue.class.getName();
        }
        if (GridSet.class.isAssignableFrom(concreteClass)) {
            return GridSet.class.getName();
        }
        return GridMap.class.getName();
    }
}
