/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.crawler.web.event.listeners;

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.mockserver.model.HttpResponse;
import org.mockserver.model.HttpStatusCode;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;

@MockServerSettings
class UrlStatusCrawlerEventListenerTest {
    @WebCrawlTest
    void testURLStatusCrawlerEventListener(
            ClientAndServer client, WebCrawlerConfig cfg) throws IOException {

        var urlStatusListener = new UrlStatusCrawlerEventListener();
        urlStatusListener.getConfiguration()
                .setTimestamped(true)
                .setStatusCodes("200-299, 400-499, 500")
                .setFileNamePrefix("super-")
                .setOutputDir(cfg.getWorkDir().resolve("statuses"));

        var ok1Path = "/ok1.html";
        var ok2Path = "/ok2.html";
        var notFoundPath = "/notFound.html";
        var errorPath = "/error.html";

        MockWebsite.whenHtml(client, ok1Path, "This page is OK.");
        MockWebsite.whenHtml(client, ok2Path, "This page is OK.");

        client.when(request(notFoundPath))
                .respond(HttpResponse.notFoundResponse());

        client.when(request(errorPath))
                .respond(response().withStatusCode(
                        HttpStatusCode.INTERNAL_SERVER_ERROR_500.code())
                        .withReasonPhrase("Kaput!"));

        cfg.setStartReferences(List.of(
                serverUrl(client, ok1Path),
                serverUrl(client, notFoundPath),
                serverUrl(client, ok2Path),
                serverUrl(client, errorPath)))
                .addEventListener(urlStatusListener);
        WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        var file = FileUtils.listFiles(
                cfg.getWorkDir().resolve("statuses").toFile(), null, false)
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
                "\"\",%sok2.html,200,OK".formatted(baseUrl));

        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT.assertWriteRead(urlStatusListener));

        // run again without any status codes, which should equate "any" and
        // return all again.
        urlStatusListener.getConfiguration().setStatusCodes("");
        cfg.setStartReferences(List.of(
                serverUrl(client, ok1Path),
                serverUrl(client, notFoundPath),
                serverUrl(client, ok2Path),
                serverUrl(client, errorPath)))
                .addEventListener(urlStatusListener);
        WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();
        assertThat(csvLines).size().isEqualTo(5);

        // test with inverted range
        urlStatusListener.getConfiguration().setStatusCodes("299-200");
        assertThatExceptionOfType(
                IllegalArgumentException.class).isThrownBy(//NOSONAR
                        () -> {
                            cfg.setStartReferences(List.of("http://blah.com"))
                                    .addEventListener(urlStatusListener);
                            WebCrawlTestCapturer.crawlAndCapture(cfg)
                                    .getCommitter();
                        });

        // test with range of too many segments
        urlStatusListener.getConfiguration().setStatusCodes("200-300-400");
        assertThatExceptionOfType(
                IllegalArgumentException.class).isThrownBy(//NOSONAR
                        () -> {
                            cfg.setStartReferences(List.of("http://blah.com"))
                                    .addEventListener(urlStatusListener);
                            WebCrawlTestCapturer.crawlAndCapture(cfg)
                                    .getCommitter();
                        });

        // test with invalid range number
        urlStatusListener.getConfiguration().setStatusCodes("123XYZ");
        assertThatExceptionOfType(
                IllegalArgumentException.class).isThrownBy(//NOSONAR
                        () -> {
                            cfg.setStartReferences(List.of("http://blah.com"))
                                    .addEventListener(urlStatusListener);
                            WebCrawlTestCapturer.crawlAndCapture(cfg)
                                    .getCommitter();
                        });
    }
}
