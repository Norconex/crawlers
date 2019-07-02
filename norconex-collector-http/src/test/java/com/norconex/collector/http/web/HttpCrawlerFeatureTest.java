/* Copyright 2019 Norconex Inc.
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
package com.norconex.collector.http.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.collector.http.HttpCollector;
import com.norconex.collector.http.HttpCollectorConfig;
import com.norconex.collector.http.TestUtil;
import com.norconex.collector.http.server.TestServer;
import com.norconex.collector.http.server.TestServerBuilder;
import com.norconex.collector.http.web.features.CanonicalLink;
import com.norconex.collector.http.web.features.CanonicalRedirectLoop;
import com.norconex.collector.http.web.features.ContentTypeCharset;
import com.norconex.collector.http.web.features.FileNotFoundDeletion;
import com.norconex.collector.http.web.features.KeepDownloads;
import com.norconex.collector.http.web.features.MaxDepth;
import com.norconex.collector.http.web.features.MaxURLs;
import com.norconex.collector.http.web.features.ModifiedFiles;
import com.norconex.collector.http.web.features.MultiRedirect;
import com.norconex.collector.http.web.features.Redirect;
import com.norconex.collector.http.web.features.RedirectCanonicalLoop;
import com.norconex.collector.http.web.features.ScriptTags;
import com.norconex.collector.http.web.features.SitemapURLDeletion;
import com.norconex.collector.http.web.features.SpecialURLs;
import com.norconex.collector.http.web.features.Timeout;
import com.norconex.collector.http.web.features.UserAgent;
import com.norconex.collector.http.web.features.ValidMetadata;
import com.norconex.collector.http.web.features.ZeroLength;
import com.norconex.collector.http.web.recovery.ResumeAfterStopped;
import com.norconex.collector.http.web.recovery.StartAfterStopped;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.file.FileUtil;

//TODO make this reusable by passing a list of ITestFeature?
/**
 * @author Pascal Essiembre
 */
public class HttpCrawlerFeatureTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCrawlerFeatureTest.class);

    private static final List<ITestFeature> FEATURES = Arrays.asList(
        // Misc. feature tests
        new Redirect(),
        new MultiRedirect(),
        new CanonicalLink(),
        new CanonicalRedirectLoop(),
        new RedirectCanonicalLoop(),
        new UserAgent(),
        new KeepDownloads(),
        new FileNotFoundDeletion(),
        new MaxURLs(),
        new SpecialURLs(),
        new ScriptTags(),
        new ZeroLength(),
        new Timeout(),
        new ContentTypeCharset(),
        new ModifiedFiles(),
        new SitemapURLDeletion(),
        new MaxDepth(),
        new ValidMetadata(),

        // Recovery-related tests
        // TODO make a separate test provider method for those?
        new StartAfterStopped(),
        new ResumeAfterStopped()
    );
    private static final Map<String, ITestFeature> FEATURES_BY_PATH =
            new HashMap<>();
    static {
        for (ITestFeature feature : FEATURES) {
            FEATURES_BY_PATH.put(feature.getPath(), feature);
        }
    }

    private static TestServer server = new TestServerBuilder()
            .addServlet(new HttpServlet() {
        private static final long serialVersionUID = 1L;
        @Override
        protected void service(
                HttpServletRequest req, HttpServletResponse resp)
                        throws ServletException, IOException {
            String path = req.getPathInfo();
            path = StringUtils.stripStart(path, "/");
            path = StringUtils.substringBefore(path, "/");

            if ("favicon.ico".equals(path)) {
                return;
            }
            if (StringUtils.isBlank(path)) {
                htmlIndexPage(req, resp);
                return;
            }

            ITestFeature feature = FEATURES_BY_PATH.get(path);
            if (feature == null) {
                throw new ServletException(
                        "Test feature/path does not exist: " + path);
            }
            try {
                feature.service(req, resp);
                resp.flushBuffer();
            } catch (Exception e) {
                throw new ServletException(
                        "Could not service feature " + path, e);
            }
        }
    }, "/*").build();

    private static String serverBaseURL;

    @TempDir
    Path tempFolder;

    public static void main(String[] args) throws IOException {
        LOG.info("Manual starting of feature test server. You will have "
               + "to kill it manually.");
        beforeClass();
    }

    @BeforeAll
    public static void beforeClass() throws IOException {
        LOG.info("Starting test server.");
        server.start();
        serverBaseURL = "http://localhost:" + server.getPort() + "/";
        LOG.info("Base URL: {}", serverBaseURL);
    }
    @AfterAll
    public static void afterClass() throws IOException {
        LOG.info("Stopping test server.");
        server.stop();
        LOG.info("Test server stopped.");
    }

    @ParameterizedTest(name = "feature: {0}")
    @MethodSource("featuresProvider")
    public void testFeature(ITestFeature feature) throws Exception {
        String uuid = UUID.randomUUID().toString();
        Path workdir = tempFolder.resolve("workdir" + uuid);
        String startURL = serverBaseURL + feature.getPath();
        for (int i = 0; i < feature.numberOfRun(); i++) {
            feature.initCurrentRunIndex(i);
            HttpCollectorConfig cfg =
                    TestUtil.newMemoryCollectorConfig(uuid, workdir, startURL);
            feature.configureCollector(cfg);
            HttpCollector collector = new HttpCollector(cfg);
            collector.start(false);
            feature.test(collector);
            ageFiles(workdir);
        }
    }

    static Stream<ITestFeature> featuresProvider() {
        return FEATURES.stream();
    }

    private static void htmlIndexPage(
                HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.toString());
        PrintWriter out = resp.getWriter();
        out.println("<html style=\"font-family:Arial, "
                + "Helvetica, sans-serif;\"><body>");
        out.println("<h1>Available test features.</h1>");
        out.println("<ul>");
        for (ITestFeature feature : FEATURES) {
            out.println("<li><a href=\"" + feature.getPath()
                    + "\">" + feature.getPath() + "</a></li>");
        }
        out.println("</ul>");
        out.println("</body></html>");
    }

    // Age progress files to fool activity tracker so we can restart right away.
    private void ageFiles(Path dir) {
        final long age = System.currentTimeMillis() - (10 * 1000);
        FileUtil.visitAllFiles(
                dir.toFile(), file -> file.setLastModified(age));
        // sleep 1 second to make sure new dir is created with new timestamp.
        Sleeper.sleepSeconds(1);
    }
}
