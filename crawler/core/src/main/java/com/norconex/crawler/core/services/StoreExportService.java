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
package com.norconex.crawler.core.services;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.mutable.MutableLong;

import com.norconex.commons.lang.file.FileUtil;
import com.norconex.crawler.core.CrawlerContext;
import com.norconex.crawler.core.CrawlerException;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.grid.GridService;
import com.norconex.crawler.core.grid.GridStore;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class StoreExportService implements GridService {

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Args implements Serializable {
        private static final long serialVersionUID = 1L;
        private String exportDir;
        private boolean pretty;
    }

    private Args args;

    @Override
    public void init(CrawlerContext crawlerContext, @NonNull String arg) {
        Thread.currentThread().setName(crawlerContext.getId() + "/EXPORT");
        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_STORE_EXPORT_BEGIN)
                .source(this)
                .message("Exporting crawler storage.")
                .build());
        args = SerialUtil.fromJson(arg, Args.class);
    }

    @Override
    public void start(CrawlerContext crawlerContext) {

        try {
            var storage = crawlerContext.getGrid().storage();
            var exportDir = Path.of(args.exportDir);
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
                        exportStore(crawlerContext, store, zipOS, type);
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
        } catch (Exception e) {
            throw new CrawlerException(
                    "A problem occured while exporting crawler storage.", e);
        }
    }

    @Override
    public void end(CrawlerContext crawlerContext) {
        crawlerContext.fire(CrawlerEvent
                .builder()
                .name(CrawlerEvent.CRAWLER_STORE_EXPORT_END)
                .source(this)
                .message("Done exporting crawler store.")
                .build());
    }

    private void exportStore(
            CrawlerContext crawlerContext,
            GridStore<?> store,
            OutputStream out,
            Class<?> type) throws IOException {

        var writer = SerialUtil.jsonGenerator(out);
        if (args.pretty) {
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
