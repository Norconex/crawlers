/* Copyright 2019-2021 Norconex Inc.
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
import java.util.Map;
import java.util.stream.Stream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.collections4.map.ListOrderedMap;
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
import com.norconex.collector.http.web.feature.CanonicalLink;
import com.norconex.collector.http.web.feature.CanonicalRedirectLoop;
import com.norconex.collector.http.web.feature.ContentTypeCharset;
import com.norconex.collector.http.web.feature.Deduplication;
import com.norconex.collector.http.web.feature.FileNotFoundDeletion;
import com.norconex.collector.http.web.feature.HttpFetcherAccept;
import com.norconex.collector.http.web.feature.IfModifiedSince;
import com.norconex.collector.http.web.feature.IfNoneMatch;
import com.norconex.collector.http.web.feature.JavaScriptURL;
import com.norconex.collector.http.web.feature.KeepDownloads;
import com.norconex.collector.http.web.feature.LargeContent;
import com.norconex.collector.http.web.feature.MaxDepth;
import com.norconex.collector.http.web.feature.MaxURLs;
import com.norconex.collector.http.web.feature.ModifiedFiles;
import com.norconex.collector.http.web.feature.MultiRedirect;
import com.norconex.collector.http.web.feature.PostImportLinks;
import com.norconex.collector.http.web.feature.Redirect;
import com.norconex.collector.http.web.feature.RedirectCanonicalLoop;
import com.norconex.collector.http.web.feature.RejectedRefsDeletion;
import com.norconex.collector.http.web.feature.ScriptTags;
import com.norconex.collector.http.web.feature.SitemapURLDeletion;
import com.norconex.collector.http.web.feature.SpecialURLs;
import com.norconex.collector.http.web.feature.Timeout;
import com.norconex.collector.http.web.feature.UnmodifiedMeta;
import com.norconex.collector.http.web.feature.UserAgent;
import com.norconex.collector.http.web.feature.ValidMetadata;
import com.norconex.collector.http.web.feature.ZeroLength;
import com.norconex.collector.http.web.recovery.ResumeAfterStopped;
import com.norconex.collector.http.web.recovery.StartAfterStopped;
import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.TimeIdGenerator;
import com.norconex.commons.lang.file.FileUtil;

//TODO make this reusable by passing a list of ITestFeature?
/**
 * @author Pascal Essiembre
 */
public class HttpCrawlerWebTest {

    private static final Logger LOG =
            LoggerFactory.getLogger(HttpCrawlerWebTest.class);

    public static final String ATTR_HTTP_PORT = "http.port";
    public static final String ATTR_HTTPS_PORT = "https.port";

    private static final Map<String, IWebTest> FEATURES_BY_PATH = toMap(

        // Misc. feature tests

        new HttpFetcherAccept(),
        new LargeContent(),
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
        new JavaScriptURL(),
        new UnmodifiedMeta(),
        new IfModifiedSince(),
        new IfNoneMatch(),
        new PostImportLinks(),
        new Deduplication(),
        new RejectedRefsDeletion(),

        // Recovery-related tests
        new StartAfterStopped(),
        new ResumeAfterStopped()


        // Disabled: depends on external site
        //new StrictTransportSecurity()



//        new StartAfterJvmCrash()
//        new ResumeAfterJvmCrashMvStore(),
//        new ResumeAfterJvmCrashDerby(),
//        new ResumeAfterJvmCrashH2()
    );
    //    private static final Map<String, ITestFeature> FEATURES_BY_PATH = toMap();
//            new HashMap<>();
//    static {
//        for (ITestFeature feature : FEATURES) {
//            FEATURES_BY_PATH.put(feature.getPath(), feature);
//        }
//    }

    private static TestServer server = new TestServerBuilder()
            .addPackage("website/static")
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

            // Add ports as convinience for tests that may need it.
            req.setAttribute(ATTR_HTTP_PORT, server.getPort());
            req.setAttribute(ATTR_HTTPS_PORT, server.getSecurePort());


            IWebTest feature = FEATURES_BY_PATH.get(path);
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

    public static void main(String[] args) {
        LOG.info("Manual starting of feature test server. You will have "
               + "to kill it manually.");
        beforeClass();
    }

    @BeforeAll
    public static void beforeClass() {
        LOG.info("Starting test server.");
        server.start();
        serverBaseURL = "http://localhost:" + server.getPort() + "/";
        LOG.info("Base URL: {}", serverBaseURL);
    }
    @AfterAll
    public static void afterClass() {
        LOG.info("Stopping test server.");
        server.stop();
        LOG.info("Test server stopped.");
    }

    @ParameterizedTest(name = "feature: {0}")
    @MethodSource(value= {
            "featuresProvider"
    })
    public void testFeature(IWebTest feature) throws Exception {
        String uuid = Long.toString(TimeIdGenerator.next());
        Path workdir = tempFolder.resolve("workdir" + uuid);
        String startURL = serverBaseURL + feature.getPath();
        for (int i = 0; i < feature.numberOfRun(); i++) {
            LOG.info("Test run #{}.", i+1);
            feature.initRunIndex(i);
            HttpCollectorConfig cfg =
                    TestUtil.newMemoryCollectorConfig(uuid, workdir, startURL);
            feature.configureCollector(cfg);
            HttpCollector collector = new HttpCollector(cfg);
            feature.startCollector(collector);
            feature.test(collector);
            ageFiles(workdir);
        }
    }

    static Stream<IWebTest> featuresProvider() {
        return FEATURES_BY_PATH.values().stream();
    }

    private static void htmlIndexPage(
                HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.setContentType("text/html");
        resp.setCharacterEncoding(StandardCharsets.UTF_8.toString());
        PrintWriter out = resp.getWriter();
        out.println("<html style=\"font-family:Arial, "
                + "Helvetica, sans-serif;\"><body>");
        out.println("<h1>Available test</h1>");
        out.println("<h3>Feature tests</h3>");
        out.println("<ul>");
        for (String path : FEATURES_BY_PATH.keySet()) {
            out.println("<li><a href=\"" + path + "\">" + path + "</a></li>");
        }
        out.println("</ul>");

//        out.println("<h3>Recovery tests</h3>");
//        out.println("<ul>");
//        for (String path : RECOVERY_BY_PATH.keySet()) {
//            out.println("<li><a href=\"" + path + "\">" + path + "</a></li>");
//        }
//        out.println("</ul>");
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
    private static final Map<String, IWebTest> toMap(IWebTest... tfs) {
        Map<String, IWebTest> map = new ListOrderedMap<>();
        for (IWebTest tf : tfs) {
            map.put(tf.getPath(), tf);
        }
        return map;
    }
}
