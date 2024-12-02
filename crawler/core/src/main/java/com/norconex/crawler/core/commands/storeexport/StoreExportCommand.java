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
package com.norconex.crawler.core.commands.storeexport;

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
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.commands.Command;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridStore;
import com.norconex.crawler.core.util.ConcurrentUtil;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class StoreExportCommand implements Command {

    private final Path exportDir;
    private final boolean pretty;

    @Override
    public void execute(CrawlerContext ctx) {
        Thread.currentThread().setName(ctx.getId() + "/STORE_EXPORT");
        ctx.fire(CrawlerEvent.CRAWLER_STORE_EXPORT_BEGIN);
        try {
            ConcurrentUtil.block(ctx.getGrid().compute().runLocalOnce(
                    StoreExportCommand.class.getSimpleName(), () -> {
                        exportAllStores(ctx);
                        return null;
                    }));
        } catch (Exception e) {
            throw new CrawlerException(
                    "A problem occured while exporting crawler storage.", e);
        }
        ctx.fire(CrawlerEvent.CRAWLER_STORE_EXPORT_END);
    }

    private void exportAllStores(CrawlerContext crawlerContext)
            throws IOException {
        var storage = crawlerContext.getGrid().storage();
        Files.createDirectories(exportDir);

        var outFile = exportDir.resolve(
                FileUtil.toSafeFileName(crawlerContext.getId() + ".zip"));
        LOG.info("Exporting crawler storage to file: {}", outFile);

        try (var zipOS = new ZipOutputStream(
                IOUtils.buffer(Files.newOutputStream(outFile)), UTF_8)) {
            storage.forEachStore(store -> {
                var name = store.getName();
                var type = store.getType();
                try {
                    zipOS.putNextEntry(new ZipEntry(
                            FileUtil.toSafeFileName(name) + ".json"));
                    exportOneStore(crawlerContext, store, zipOS, type);
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
            CrawlerContext crawlerContext,
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
        writer.writeStringField("crawler", crawlerContext.getId());
        writer.writeStringField("store", store.getName());
        writer.writeStringField("storeType", store.getType().getName());
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
}
