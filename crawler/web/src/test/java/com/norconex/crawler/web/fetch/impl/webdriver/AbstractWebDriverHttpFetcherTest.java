/* Copyright 2018-2024 Norconex Inc.
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
package com.norconex.crawler.web.fetch.impl.webdriver;

import static com.norconex.crawler.web.mocks.MockWebsite.serverUrl;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.MediaType.HTML_UTF_8;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.List;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.junit.jupiter.MockServerSettings;
import org.openqa.selenium.Capabilities;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.BrowserWebDriverContainer.VncRecordingMode;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import com.norconex.commons.lang.bean.BeanMapper;
import com.norconex.commons.lang.config.Configurable;
import com.norconex.commons.lang.img.MutableImage;
import com.norconex.crawler.core.doc.DocResolutionStatus;
import com.norconex.crawler.core.fetch.FetchException;
import com.norconex.crawler.web.WebCrawlerConfig;
import com.norconex.crawler.web.WebTestUtil;
import com.norconex.crawler.web.fetch.HttpFetchRequest;
import com.norconex.crawler.web.fetch.HttpMethod;
import com.norconex.crawler.web.fetch.util.DocImageHandlerConfig.Target;
import com.norconex.crawler.web.junit.WebCrawlTest;
import com.norconex.crawler.web.junit.WebCrawlTestCapturer;
import com.norconex.crawler.web.mocks.MockWebsite;
import com.norconex.crawler.web.stubs.CrawlDocStubs;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@MockServerSettings
@TestInstance(Lifecycle.PER_CLASS)
@org.testcontainers.junit.jupiter.Testcontainers(disabledWithoutDocker = true)

public abstract class AbstractWebDriverHttpFetcherTest
        implements ExecutionCondition {

    private static final int LARGE_CONTENT_MIN_SIZE = 3 * 1024 * 1024;

    private final Capabilities capabilities;
    private BrowserWebDriverContainer<?> browser;
    private Browser browserType;

    public AbstractWebDriverHttpFetcherTest(Browser browserType) {
        if (browserType == Browser.CHROME) {
            capabilities = new ChromeOptions();
        } else if (browserType == Browser.FIREFOX) {
            capabilities = new FirefoxOptions();
        } else {
            throw new IllegalArgumentException(
                    "Only Chrome and Firefox are supported for testing.");
        }
        this.browserType = browserType;
    }

    @BeforeAll
    void beforeAll() {
        browser = createWebDriverContainer(capabilities);
        browser.start();
    }

    @AfterAll
    void afterAll() {
        browser.stop();
    }

    @BeforeEach
    void beforeEach(ClientAndServer client) {
        Testcontainers.exposeHostPorts(client.getPort());
    }

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(
            ExtensionContext ctx) {
        try {
            DockerClientFactory.instance().client();
            return ConditionEvaluationResult.enabled(
                    "Docker found: WebDriverHttpFetcher tests will run.");
        } catch (Throwable ex) {
            return ConditionEvaluationResult.enabled(
                    "Docker NOT found: WebDriverHttpFetcher tests will be "
                            + "disabled and will not run.");
        }
    }

    @WebCrawlTest
    void testFetchingJsGeneratedContent(
            ClientAndServer client, WebCrawlerConfig cfg) {
        MockWebsite.whenJsRenderedWebsite(client);

        cfg.setFetchers(List.of(createWebDriverHttpFetcher()));
        cfg.setMaxDepth(0);
        cfg.setStartReferences(List.of(hostUrl(client, "/index.html")));
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        assertThat(mem.getRequestCount()).isOne();
        assertThat(WebTestUtil.docText(mem.getUpsertRequests().get(0)))
                .contains("JavaScript-rendered!");
    }

    @WebCrawlTest
    void testTakeScreenshots(ClientAndServer client, WebCrawlerConfig cfg)
            throws IOException {

        MockWebsite.whenJsRenderedWebsite(client);

        var h = new ScreenshotHandler();
        h.getConfiguration()
                .setCssSelector("#applePicture")
                .setTargets(List.of(Target.METADATA))
                .setTargetMetaField("myimage");

        var f = createWebDriverHttpFetcher();
        f.getConfiguration().setScreenshotHandler(h);
        cfg.setFetchers(List.of(f));
        cfg.setMaxDepth(0);
        cfg.setStartReferences(List.of(hostUrl(client, "/apple.html")));
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        assertThat(mem.getUpsertCount()).isOne();

        var img = MutableImage.fromBase64String(
                mem.getUpsertRequests().get(0).getMetadata().getString(
                        "myimage"));
        assertThat(img).isNotNull();
        // Chrome, and maybe others, may resize the image to make it smaller
        // so that affects the max crop we can get. That's why we don't
        // check for exact dimension.
        assertThat(img.getWidth()).isLessThanOrEqualTo(350);
        assertThat(img.getHeight()).isLessThanOrEqualTo(350);
    }

    // Test using sniffer for capturing HTTP response headers and
    // using sniffer with large content (test case for:
    // https://github.com/Norconex/collector-http/issues/751)
    @WebCrawlTest
    void testHttpSniffer(ClientAndServer client, WebCrawlerConfig cfg) {

        var path = "/sniffHeaders.html";

        // @formatter:off
        client
            .when(request(path))
            .respond(response()
                .withHeader("multiKey", "multiVal1", "multiVal2")
                .withHeader("singleKey", "singleValue")
                .withBody(MockWebsite
                    .htmlPage()
                    .body(RandomStringUtils
                            .randomAlphanumeric(LARGE_CONTENT_MIN_SIZE))
                    .build()));
        // @formatter:on

        var sniffer = new HttpSniffer();
        var snifCfg = sniffer.getConfiguration();
        snifCfg.setHost("host.testcontainers.internal");
        snifCfg.setPort(freePort());
        // also test sniffer with large content
        snifCfg.setMaxBufferSize(6 * 1024 * 1024);
        LOG.debug("Random HTTP Sniffer proxy port: {}", snifCfg.getPort());
        Testcontainers.exposeHostPorts(client.getPort(), snifCfg.getPort());
        var fetcher = createWebDriverHttpFetcher();
        fetcher.getConfiguration().setHttpSniffer(sniffer);
        cfg.setFetchers(List.of(fetcher));
        cfg.setMaxDepth(0);
        cfg.setStartReferences(List.of(serverUrl(client, path)));
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        assertThat(mem.getUpsertRequests()).hasSize(1);
        var meta = mem.getUpsertRequests().get(0).getMetadata();
        assertThat(meta.getStrings("multiKey")).containsExactly(
                "multiVal1", "multiVal2");
        assertThat(meta.getString("singleKey")).isEqualTo("singleValue");

        assertThat(WebTestUtil.docText(mem.getUpsertRequests().get(0)).length())
                .isGreaterThanOrEqualTo(LARGE_CONTENT_MIN_SIZE);
    }

    @WebCrawlTest
    void testPageScript(ClientAndServer client, WebCrawlerConfig cfg) {
        var path = "/pageScript.html";

        // @formatter:off
        client
            .when(request(path))
            .respond(response()
                .withBody(MockWebsite
                    .htmlPage()
                    .body("""
                          <h1>Page Script Test</h1>
                          <p>H1 above should be replaced.</p>
                          """)
                    .build(),
                    HTML_UTF_8));
        // @formatter:on

        var f = createWebDriverHttpFetcher();
        f.getConfiguration().setEarlyPageScript(
                "document.title='Awesome!';");
        f.getConfiguration().setLatePageScript("""
                    document.getElementsByTagName('h1')[0].innerHTML='Melon';
                    """);
        cfg.setFetchers(List.of(f));
        cfg.setMaxDepth(0);
        cfg.setStartReferences(List.of(hostUrl(client, path)));
        var mem = WebCrawlTestCapturer.crawlAndCapture(cfg).getCommitter();

        var doc = mem.getUpsertRequests().get(0);
        assertThat(doc.getMetadata()
                .getString("dc:title"))
                        .isEqualTo("Awesome!");
        assertThat(WebTestUtil.docText(doc)).contains("Melon");
    }

    @WebCrawlTest
    void testResolvingUserAgent(
            ClientAndServer client, WebCrawlerConfig cfg) {
        var path = "/userAgent.html";

        // @formatter:off
        client
            .when(request(path))
            .respond(response()
                .withBody(MockWebsite
                    .htmlPage()
                    .body("<p>Should grab user agent from browser.</p>")
                    .build(),
                    HTML_UTF_8));
        // @formatter:on

        var fetcher = createWebDriverHttpFetcher();
        cfg.setFetchers(List.of(fetcher));
        cfg.setMaxDepth(0);
        // test setting a bunch of other params
        fetcher.getConfiguration()
                .setWindowSize(new java.awt.Dimension(640, 480))
                .setPageLoadTimeout(Duration.ofSeconds(10))
                .setImplicitlyWait(Duration.ofSeconds(1))
                .setScriptTimeout(Duration.ofSeconds(10))
                .setWaitForElementSelector("p")
                .setWaitForElementTimeout(Duration.ofSeconds(10));
        cfg.setStartReferences(List.of(hostUrl(client, path)));
        WebCrawlTestCapturer.crawlAndCapture(cfg);
        assertThat(fetcher.getUserAgent()).isNotBlank();
    }

    @Test
    void testUnsupportedHttpMethod() throws FetchException {
        var response = new WebDriverFetcher().fetch(
                new HttpFetchRequest(
                        CrawlDocStubs.crawlDocHtml("http://example.com"),
                        HttpMethod.HEAD));
        assertThat(response.getReasonPhrase()).contains("To obtain headers");
        assertThat(response.getResolutionStatus()).isEqualTo(
                DocResolutionStatus.UNSUPPORTED);
    }

    @Test
    void testWriteRead() {
        assertThatNoException().isThrownBy(
                () -> BeanMapper.DEFAULT
                        .assertWriteRead(new WebDriverFetcher()));
    }

    //--- Private/Protected ----------------------------------------------------

    private WebDriverFetcher createWebDriverHttpFetcher() {
        return Configurable.configure(
                new WebDriverFetcher(), cfg -> cfg
                        .setBrowser(browserType)
                        .setRemoteURL(browser.getSeleniumAddress()));
    }

    private String hostUrl(ClientAndServer client, String path) {
        return "http://host.testcontainers.internal:%s%s".formatted(
                client.getLocalPort(), path);
    }

    private int freePort() {
        try (var serverSocket = new ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @SuppressWarnings("resource")
    protected static BrowserWebDriverContainer<?> createWebDriverContainer(
            Capabilities capabilities) {
        return new BrowserWebDriverContainer<>()
                .withCapabilities(capabilities)
                .withAccessToHost(true)
                .withRecordingMode(VncRecordingMode.SKIP, null)
                .withLogConsumer(new Slf4jLogConsumer(LOG));
    }
}
