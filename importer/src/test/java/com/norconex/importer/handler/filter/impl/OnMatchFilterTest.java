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
package com.norconex.importer.handler.filter.impl;

import static com.norconex.importer.response.ImporterStatus.Status.REJECTED;
import static com.norconex.importer.response.ImporterStatus.Status.SUCCESS;

import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.ImporterRequest;
import com.norconex.importer.handler.HandlerConsumer;
import com.norconex.importer.handler.HandlerDoc;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.DocumentFilter;
import com.norconex.importer.handler.filter.OnMatchFilter;
import com.norconex.importer.handler.filter.OnMatch;
import com.norconex.importer.parser.ParseState;
import com.norconex.importer.response.ImporterResponse;
import com.norconex.importer.response.ImporterStatus.Status;

public class OnMatchFilterTest {

    @Test
    public void testWithOnMatchFilter() {
        testFilter(new WithOnMatchFilter(OnMatch.INCLUDE, true), SUCCESS);
        testFilter(new WithOnMatchFilter(OnMatch.INCLUDE, false), REJECTED);
        testFilter(new WithOnMatchFilter(OnMatch.EXCLUDE, true), REJECTED);
        testFilter(new WithOnMatchFilter(OnMatch.EXCLUDE, false), SUCCESS);
    }

    @Test
    public void testWithoutOnMatchFilter() {
        testFilter(new WithoutOnMatchFilter(true), SUCCESS);
        testFilter(new WithoutOnMatchFilter(false), Status.REJECTED);
    }

    private void testFilter(DocumentFilter f, Status expectedStatus) {
        InputStream is = new CachedStreamFactory(10, 10).newInputStream();
        Properties meta = new Properties();
        ImporterConfig cfg = new ImporterConfig();
        cfg.setPreParseConsumer(HandlerConsumer.fromHandlers(f));
        Importer importer = new Importer(cfg);
        ImporterResponse r = importer.importDocument(
                new ImporterRequest(is).setReference("N/A").setMetadata(meta));
        Assertions.assertEquals(
                expectedStatus, r.getImporterStatus().getStatus());
    }

    private class WithOnMatchFilter implements DocumentFilter, OnMatchFilter {
        private final OnMatch onMatch;
        private final boolean matched;
        public WithOnMatchFilter(OnMatch onMatch, boolean matched) {
            super();
            this.onMatch = onMatch;
            this.matched = matched;
        }
        @Override
        public OnMatch getOnMatch() {
            return onMatch;
        }
        @Override
        public boolean acceptDocument(HandlerDoc doc, InputStream input,
                ParseState parseState) throws ImporterHandlerException {
            return matched && onMatch == OnMatch.INCLUDE
                || !matched && onMatch == OnMatch.EXCLUDE;
        }
    }
    private class WithoutOnMatchFilter implements DocumentFilter {
        private final boolean accept;
        public WithoutOnMatchFilter(boolean accept) {
            super();
            this.accept = accept;
        }
        @Override
        public boolean acceptDocument(HandlerDoc doc, InputStream input,
                ParseState parseState) throws ImporterHandlerException {
            return accept;
        }
    }

}
