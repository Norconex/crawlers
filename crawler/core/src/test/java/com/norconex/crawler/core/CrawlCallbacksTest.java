/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.commons.lang.event.Event;
import com.norconex.crawler.core.cmd.crawl.CrawlCommand;
import com.norconex.crawler.core.event.CrawlerEvent;
import com.norconex.crawler.core.session.CrawlSession;

@Timeout(30)
class CrawlCallbacksTest {

    private CrawlConfig mockConfig() {
        var cfg = new CrawlConfig();
        cfg.setId("test");
        return cfg;
    }

    @Test
    void accept_nonCrawlerEvent_isIgnored() {
        List<String> received = new ArrayList<>();
        var callbacks = CrawlCallbacks.builder()
                .beforeSession(cfg -> received.add("before"))
                .build();

        // Generic non-CrawlerEvent → should be ignored
        callbacks.accept(Event.builder()
                .name("SOME_OTHER_EVENT")
                .source("source")
                .build());

        assertThat(received).isEmpty();
    }

    @Test
    void accept_sessionBegin_invokesBeforeSession() {
        List<CrawlConfig> received = new ArrayList<>();
        var cfg = mockConfig();
        var callbacks = CrawlCallbacks.builder()
                .beforeSession(received::add)
                .build();

        var event = CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_SESSION_BEGIN)
                .source(cfg)
                .build();
        callbacks.accept(event);

        assertThat(received).containsExactly(cfg);
    }

    @Test
    void accept_sessionEnd_invokesAfterSession() {
        List<CrawlConfig> received = new ArrayList<>();
        var cfg = mockConfig();
        var callbacks = CrawlCallbacks.builder()
                .afterSession(received::add)
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_SESSION_END)
                .source(cfg)
                .build());

        assertThat(received).containsExactly(cfg);
    }

    @Test
    void accept_commandBegin_invokesBeforeCommand() {
        List<Class<?>> commandClasses = new ArrayList<>();
        var session = mock(CrawlSession.class);
        var callbacks = CrawlCallbacks.builder()
                .beforeCommand((s, cls) -> commandClasses.add(cls))
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_COMMAND_BEGIN)
                .source(mockConfig())
                .crawlSession(session)
                .commandClass(CrawlCommand.class)
                .build());

        assertThat(commandClasses).containsExactly(CrawlCommand.class);
    }

    @Test
    void accept_commandEnd_invokesAfterCommand() {
        List<Class<?>> commandClasses = new ArrayList<>();
        var session = mock(CrawlSession.class);
        var callbacks = CrawlCallbacks.builder()
                .afterCommand((s, cls) -> commandClasses.add(cls))
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_COMMAND_END)
                .source(mockConfig())
                .crawlSession(session)
                .commandClass(CrawlCommand.class)
                .build());

        assertThat(commandClasses).containsExactly(CrawlCommand.class);
    }

    @Test
    void accept_documentProcessingBegin_invokesBeforeDocProcessing() {
        List<Object> received = new ArrayList<>();
        var session = mock(CrawlSession.class);
        var docSource = mock(com.norconex.importer.doc.Doc.class);
        var callbacks = CrawlCallbacks.builder()
                .beforeDocumentProcessing((s, doc) -> received.add(doc))
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_PROCESSING_BEGIN)
                .source(docSource)
                .crawlSession(session)
                .build());

        assertThat(received).containsExactly(docSource);
    }

    @Test
    void accept_documentProcessingEnd_invokesAfterDocProcessing() {
        List<Object> received = new ArrayList<>();
        var session = mock(CrawlSession.class);
        var docSource = mock(com.norconex.importer.doc.Doc.class);
        var callbacks = CrawlCallbacks.builder()
                .afterDocumentProcessing((s, doc) -> received.add(doc))
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_PROCESSING_END)
                .source(docSource)
                .crawlSession(session)
                .build());

        assertThat(received).containsExactly(docSource);
    }

    @Test
    void accept_documentFinalizingBegin_invokesBeforeDocFinalizing() {
        List<Object> received = new ArrayList<>();
        var session = mock(CrawlSession.class);
        var docSource = mock(com.norconex.importer.doc.Doc.class);
        var callbacks = CrawlCallbacks.builder()
                .beforeDocumentFinalizing((s, doc) -> received.add(doc))
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_FINALIZING_BEGIN)
                .source(docSource)
                .crawlSession(session)
                .build());

        assertThat(received).containsExactly(docSource);
    }

    @Test
    void accept_documentFinalizingEnd_invokesAfterDocFinalizing() {
        List<Object> received = new ArrayList<>();
        var session = mock(CrawlSession.class);
        var docSource = mock(com.norconex.importer.doc.Doc.class);
        var callbacks = CrawlCallbacks.builder()
                .afterDocumentFinalizing((s, doc) -> received.add(doc))
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.DOCUMENT_FINALIZING_END)
                .source(docSource)
                .crawlSession(session)
                .build());

        assertThat(received).containsExactly(docSource);
    }

    @Test
    void accept_noCallbacksSet_doesNotThrow() {
        var callbacks = CrawlCallbacks.builder().build();
        var cfg = mockConfig();

        assertThatNoException().isThrownBy(() -> {
            callbacks.accept(CrawlerEvent.builder()
                    .name(CrawlerEvent.CRAWLER_SESSION_BEGIN)
                    .source(cfg)
                    .build());
            callbacks.accept(CrawlerEvent.builder()
                    .name(CrawlerEvent.CRAWLER_SESSION_END)
                    .source(cfg)
                    .build());
        });
    }

    @Test
    void accept_unknownEventName_doesNothing() {
        List<String> received = new ArrayList<>();
        var callbacks = CrawlCallbacks.builder()
                .beforeSession(c -> received.add("session"))
                .build();

        callbacks.accept(CrawlerEvent.builder()
                .name(CrawlerEvent.CRAWLER_CRAWL_BEGIN) // not a callback event
                .source(mockConfig())
                .build());

        assertThat(received).isEmpty();
    }

    @Test
    void crawlCommandCallback_defaultImplementation_ignoresNonCrawlCommands() {
        List<String> received = new ArrayList<>();
        CrawlCallbacks.CrawlCommandCallback cb =
                session -> received.add("crawl");

        // Test with CrawlCommand (should invoke accept(session))
        cb.accept(mock(CrawlSession.class), CrawlCommand.class);
        assertThat(received).containsExactly("crawl");

        // Test with a non-CrawlCommand class (should be ignored)
        received.clear();
        cb.accept(mock(CrawlSession.class),
                com.norconex.crawler.core.cmd.clean.CleanCommand.class);
        assertThat(received).isEmpty();
    }
}
