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
package com.norconex.crawler.web.crawler.event.impl;

import static com.norconex.crawler.web.WebsiteMock.serverUrl;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.IOException;
import java.nio.file.Path;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.web.TestWebCrawlSession;
import com.norconex.crawler.web.WebsiteMock;

@MockServerSettings
class URLStatusCrawlerEventListenerTest {

    @Test
    void testURLStatusCrawlerEventListener(
            ClientAndServer client, @TempDir Path tempDir) throws IOException {

        var urlStatusListener = new URLStatusCrawlerEventListener();
        urlStatusListener.setCombined(true);
        urlStatusListener.setTimestamped(true);
        urlStatusListener.setStatusCodes("200-299, 400-499, 500");
        urlStatusListener.setFileNamePrefix("super-");
        urlStatusListener.setOutputDir(tempDir.resolve("statuses"));


        var ok1Path = "/ok1.html";
        var ok2Path = "/ok2.html";
        var notFoundPath = "/notFound.html";
        var errorPath = "/error.html";

        WebsiteMock.whenHtml(client, ok1Path, "This page is OK.");
        WebsiteMock.whenHtml(client, ok1Path, "This page is OK.");

        client
            .when(request(notFoundPath))
            .respond(HttpResponse.notFoundResponse());

        client
            .when(request(errorPath))
            .respond(response()
                .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR_500.code())
                .withReasonPhrase("Kaput!"));

        TestWebCrawlSession
            .forStartReferences(
                    serverUrl(client, ok1Path),
                    serverUrl(client, notFoundPath),
                    serverUrl(client, ok2Path),
                    serverUrl(client, errorPath))
            .crawlerSetup(cfg -> cfg.addEventListener(urlStatusListener))
            .crawl();

        var file = FileUtils.listFiles(
                tempDir.resolve("statuses").toFile(), null, false)
                    .stream()
                    .findFirst()
                    .orElseThrow();

        var csvLines = FileUtils.readLines(file, UTF_8);
        var baseUrl = serverUrl(client, "");
        assertThat(csvLines).containsExactlyInAnyOrder(
            "Referrer,URL,Status,Reason",
            "\"\",%serror.html,500,Kaput!".formatted(baseUrl),
            "\"\",%snotFound.html,404,Not Found".formatted(baseUrl),
            "\"\",%sok1.html,200,OK".formatted(baseUrl),
            "\"\",%sok2.html,404,Not Found".formatted(baseUrl));

        assertThatNoException().isThrownBy(() -> {
            XML.assertWriteRead(urlStatusListener, "listener");
        });
    }
}
