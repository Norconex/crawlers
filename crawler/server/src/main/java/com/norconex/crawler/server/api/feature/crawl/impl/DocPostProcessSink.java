/* Copyright 2023 Norconex Inc.
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
package com.norconex.crawler.server.api.feature.crawl.impl;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.apache.commons.io.IOUtils;

import com.norconex.commons.lang.io.IOUtil;
import com.norconex.crawler.core.doc.CrawlDoc;
import com.norconex.crawler.core.fetch.Fetcher;
import com.norconex.crawler.core.processor.DocumentProcessor;
import com.norconex.crawler.server.api.feature.crawl.model.CrawlDocDTO;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.FluxSink;

/**
 * Adds documents successfully imported by the crawler to a Flux Sink.
 */
@RequiredArgsConstructor
@Slf4j
public class DocPostProcessSink implements DocumentProcessor{

    @NonNull
    private final FluxSink<Object> sink;
    private final long maxContentSize;

    @Override
    public void processDocument(Fetcher<?, ?> fetcher, CrawlDoc doc) {
        var dto = new CrawlDocDTO();
        dto.setReference(doc.getReference());
        dto.getMetadata().putAll(doc.getMetadata());

        try {
            // if zero we don't assign any content
            if (maxContentSize < 0) { // stream all to string
                dto.setContent(IOUtils.toString(doc.getInputStream(), UTF_8));
            } else if (maxContentSize > 0) { // up to max
                var b = new StringBuilder();
                try (Reader r = new BufferedReader(
                        new InputStreamReader(doc.getInputStream(), UTF_8))) {
                    IOUtil.consumeUntil(
                            r, ch -> b.length() > maxContentSize, b);
                }
                dto.setContent(b.toString());
            }
            sink.next(dto);
        } catch (IOException e) {
            //TODO return an error to client?
            LOG.warn("Could not post-process document: {}", doc.getReference());
        }
    }
}
