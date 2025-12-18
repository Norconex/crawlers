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
package com.norconex.crawler.core.cmd.storeimport;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.zip.ZipInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.norconex.crawler.core.cluster.ClusterException;
import com.norconex.crawler.core.cmd.Command;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlSession;
import com.norconex.crawler.core.util.SerialUtil;

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

        //Export/Import are meant to run on a single node only. We ensure
        // this by checking if we are the coordinator.
        if (session.getCluster().getLocalNode().isCoordinator()) {
            session.fire(CrawlerEvent.CRAWLER_STORE_IMPORT_BEGIN, this);
            try {
                importAllStores(session);
            } catch (Exception e) {
                throw new ClusterException("Could not import file: " + inFile,
                        e);
            }
            session.fire(CrawlerEvent.CRAWLER_STORE_IMPORT_END, this);
        } else {
            LOG.warn("""
                Importing can only be performed on a single node. \
                Another node started the export process so this one \
                will ignore the request.""");
        }

    }

    private void importAllStores(CrawlSession session)
            throws IOException {
        Map<String, Iterator<Entry<String, String>>> imports = new HashMap<>();
        try (var zipIn = new ZipInputStream(
                IOUtils.buffer(Files.newInputStream(inFile)))) {
            while ((zipIn.getNextEntry()) != null) {
                importOneStore(session, zipIn, imports);
                zipIn.closeEntry();
            }
        }
        var cacheManager = session.getCluster().getCacheManager();
        cacheManager.importCaches(imports);
    }

    private void importOneStore(
            CrawlSession session, InputStream in,
            Map<String, Iterator<Entry<String, String>>> imports)
            throws IOException {
        var parser = SerialUtil.jsonParser(in);

        String crawlerId = null;
        String storeName = null;

        parser.nextToken();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            var key = parser.currentName();
            if ("crawler".equals(key)) {
                crawlerId = parser.nextTextValue();
            } else if ("store".equals(key)) {
                storeName = parser.nextTextValue();
            } else if ("records".equals(key)) {
                if (StringUtils.isAnyBlank(crawlerId, storeName)) {
                    LOG.error("Invalid import file encountered for entry.");
                    return;
                }
                if (!crawlerId.equals(session.getCrawlerId())) {
                    LOG.debug("Skipping store {} for crawler {}", storeName,
                            crawlerId);
                    return;
                }
                LOG.info("Parsing records for store \"{}\" from file.",
                        storeName);
                parser.nextToken(); // start array
                imports.put(storeName, new LazyRecordIterator(parser));
            } else {
                parser.nextValue();
            }
        }
    }

    private static class LazyRecordIterator
            implements Iterator<Entry<String, String>> {

        private final JsonParser parser;
        private boolean hasNext = true;

        public LazyRecordIterator(JsonParser parser) {
            this.parser = parser;
        }

        @Override
        public boolean hasNext() {
            return hasNext;
        }

        @Override
        public Entry<String, String> next() {
            if (!hasNext) {
                throw new NoSuchElementException();
            }
            try {
                parser.nextToken(); // START_OBJECT
                parser.nextToken(); // "id"
                var id = parser.nextTextValue();
                parser.nextToken(); // "object"
                var value = parser.nextTextValue();
                parser.nextToken(); // END_OBJECT
                parser.nextToken(); // check next
                if (parser.currentToken() == JsonToken.END_ARRAY) {
                    hasNext = false;
                }
                return Map.entry(id, value);
            } catch (IOException e) {
                throw new NoSuchElementException(
                        "Error reading next record: " + e.getMessage());
            }
        }
    }

}
