/* Copyright 2014-2018 Norconex Inc.
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
package com.norconex.collector.http.website;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.text.StringSubstitutor;
import org.apache.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.map.Properties;

/**
 * @author Pascal Essiembre
 */
public class TestServlet extends HttpServlet {

    private static final long serialVersionUID = -4252570491708918968L;
    private static final Logger LOG = LoggerFactory.getLogger(TestServlet.class);

    private final Map<String, ITestCase> testCases = new HashMap<>();

    private final List<String> tokens = new ArrayList<>();

    /**
     * Constructor.
     */
    public TestServlet() {
        testCases.put("list", new ListTestCases());
        testCases.put("basic", new BasicTestCase());
        testCases.put("redirect", new RedirectTestCase());
        testCases.put("multiRedirects", new MultiRedirectsTestCase());
        testCases.put("userAgent", new UserAgentTestCase());
        testCases.put("keepDownloads", new KeepDownloadedFilesTestCase());
        testCases.put("deletedFiles", new DeletedFilesTestCase());
        testCases.put("modifiedFiles", new ModifiedFilesTestCase());
        testCases.put("canonical", new CanonicalTestCase());
        testCases.put("canonRedirLoop", new CanonicalRedirectLoopTestCase());
        testCases.put("specialURLs", new SpecialURLTestCase());
        testCases.put("script", new ScriptTestCase());
        testCases.put("zeroLength", new ZeroLengthTestCase());
        testCases.put("timeout", new TimeoutTestCase());
        testCases.put("iframe", new IFrameTestCase());
        testCases.put("contentTypeCharset", new ContentTypeCharsetTestCase());
        testCases.put("sitemap", new SitemapTestCase());
        testCases.put("merge", new MergeTestCase());
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String testCaseKey = StringUtils.trimToNull(req.getParameter("case"));
        if (StringUtils.isBlank(testCaseKey)) {
            testCaseKey = "list";
        }
        ITestCase iTestCase = testCases.get(testCaseKey);
        if (iTestCase == null) {
            iTestCase = testCases.get("list");
        }
        try {
            iTestCase.doTestCase(req, resp);
            resp.flushBuffer();
        } catch (Exception e) {
            e.printStackTrace(resp.getWriter());
        }
    }

    interface ITestCase {
        void doTestCase(HttpServletRequest req,
                HttpServletResponse resp) throws Exception;
    }
    abstract class HtmlTestCase implements ITestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp) throws Exception {
            resp.setContentType("text/html");
            resp.setCharacterEncoding(StandardCharsets.UTF_8.toString());
            PrintWriter out = resp.getWriter();
            out.println("<html style=\"font-family:Arial, "
                   + "Helvetica, sans-serif;\">");
            doTestCase(req, resp, out);
            out.println("</html>");
        }
        protected abstract void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception;

    }

    class ListTestCases extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {
            out.println("<h1>Available test cases.</h1>");
            out.println("<ul>");
            for (String testCaseKey : testCases.keySet()) {
                out.println("<li><a href=\"?case=" + testCaseKey
                        + "\">" + testCaseKey + "</a></li>");
            }
            out.println("</ul>");
        }
    }

    class BasicTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {
            Properties params = new Properties();
            params.loadFromMap(req.getParameterMap());
            int depth = params.getInteger("depth", 0);
            int prevDepth = depth - 1;
            int nextDepth = depth + 1;
            out.println("<h1>Basic features test page</h1>");
            out.println("<p>Tests: BasicFeaturesTest (depth, validMetadata), "
                    + "ExecutionTest</p>");
            if (prevDepth >= 0) {
                out.println("<a href=\"?case=basic&depth=" + prevDepth
                        + "\">Previous depth is " + prevDepth + "</a><br><br>");
            }
            out.println("<b>This page is of depth: " + depth + "</b><br><br>");
            out.println("<a href=\"?case=basic&depth=" + nextDepth
                    + "\">Next depth is " + nextDepth + "</a>");
        }
    }

    /**
     * The final URL of a redirect should be stored so relative links in it
     * are relative to final URL, not the first.  Github issue #17.
     */
    class RedirectTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            if (req.getPathInfo() == null
                    || !req.getPathInfo().contains("redirected")) {
//                System.out.println("path info is: " + req.getPathInfo());
                resp.sendRedirect("http://localhost:" + req.getLocalPort()
                        + "/test/redirected/page.html?case=redirect");
                return;
            }
            out.println("<h1>Redirected test page</h1>");
            out.println("The URL was redirected."
                    + "The URLs on this page should be relative to "
                    + "/test/redirected/ and not /.  The crawler should "
                    + "redirect and figure that out.<br><br>");
            out.println("<a href=\"page1.html\">Page 1 (broken)</a>");
            out.println("<a href=\"page2.html\">Page 2 (broken)</a>");
        }
    }

    /**
     * The tail of redirects should be kept as metadata so implementors
     * can know where documents came from.
     */
    class MultiRedirectsTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            int maxRedirects = 5;
            int count = NumberUtils.toInt(req.getParameter("count"), 0);

            if (count < maxRedirects) {
                resp.sendRedirect(
                        "/test?case=multiRedirects&count=" + (count + 1));
                return;
            }
            out.println("<h1>Multi-redirects test page</h1>");
            out.println("The URL was redirected " + maxRedirects + " times. "
                    + "Was the redirect trail kept somehwere in your crawler?"
                    + "<br>");
        }
    }

    class UserAgentTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {
            String userAgent = req.getHeader("User-Agent");
            out.println("<h1>User Agent test page</h1>");
            out.println("The user agent is: " + userAgent);
        }
    }

    class KeepDownloadedFilesTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            out.println("<h1>Keep downloaded files == true</h1>");
            out.println("<b>This</b> file <i>must</i> be saved as is, "
                    + "with this <span>formatting</span>");
        }
    }

    class DeletedFilesTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {
            String page = req.getParameter("page");
            String token = req.getParameter("token");

            if (StringUtils.isNotBlank(page)
                    &&  StringUtils.isNotBlank(token)) {
                String pageToken = "page-" + page + "-" + token;
                if (tokens.contains(pageToken)) {
                    tokens.remove(pageToken);
                    resp.sendError(HttpStatus.SC_NOT_FOUND,
                            "Not found (so they say)");
                    return;
                } else {
                    tokens.add(pageToken);
                }
            }

            if (StringUtils.isNotBlank(page)) {
                out.println("<h1>Delete test page " + page + "</h1>");
                out.println("<p>This page should give a 404 when accessed "
                        + "a second time with the same token "
                        + "(and keeps toggling on every requests).</p>");
            } else {
                out.println("<h1>Deleted files test main page</h1>");
                out.println("<p>When accessed with a <b>token</b> parameter "
                    + "having "
                    + "an arbitrary value, this page will list links "
                    + "that are valid the first time they are accessed. "
                    + "The second time they are accessed with the same token "
                    + "value, "
                    + "the links won't be found and the HTTP "
                    + "response will be a "
                    + "404 when accessing them. "
                    + "If you keep accessing the same URLs, the state "
                    + "will change on each request from this page to 404.</p>"
                    + "<ul>"
                    + "<li><a href=\"?case=deletedFiles&token=" + token
                    + "&page=1\">Delete Test Page 1</a></li>"
                    + "<li><a href=\"?case=deletedFiles&token=" + token
                    + "&page=2\">Delete Test Page 2</a></li>"
                    + "<li><a href=\"?case=deletedFiles&token=" + token
                    + "&page=3\">Delete Test Page 3</a></li>"
                    + "</ul>");
            }
        }
    }

    class ModifiedFilesTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            String page = req.getParameter("page");
            // 2001-01-01T01:01:01 GMT
            long staticDate = 978310861000l;
            if (StringUtils.isBlank(page)) {
                resp.setDateHeader("Last-Modified", staticDate);
                out.println("<h1>Modified files test main page</h1>");
                out.println("<p>While this page is never modified, the 3 links "
                    + "below point to pages that are: "
                    + "<ul>"
                    + "<li><a href=\"?case=modifiedFiles&page=1\">"
                    + "Modified Test Page 1</a>: Ever changing Last-Modified "
                    + "date in http header.</li>"
                    + "<li><a href=\"?case=modifiedFiles&page=2\">"
                    + "Modified Test Page 2</a>: Ever changing body content."
                    + "</li>"
                    + "<li><a href=\"?case=modifiedFiles&page=3\">"
                    + "Modified Test Page 3</a>: Both header and body are "
                    + "ever changing.</li>"
                    + "</ul>");
            } else if ("1".equals(page)) {
                // Wait 1 second to make sure the date has changed.
                Sleeper.sleepSeconds(1);
                resp.setDateHeader("Last-Modified", System.currentTimeMillis());
                out.println("<h1>Modified test page 1 (meta)</h1>");
                out.println("<p>This page content is always the same, but "
                        + "the Last-Modified HTTP response value is always "
                        + "the current date (so it keeps changing).</p>");
            } else if ("2".equals(page)) {
                resp.setDateHeader("Last-Modified", staticDate);
                out.println("<h1>Modified test page 2 (content)</h1>");
                out.println("<p>This page content always changes because of "
                        + "this random number: " + System.currentTimeMillis()
                        + "</p><p>The Last-Modified HTTP response value is "
                        + "always the same.</p>");
            } else if ("3".equals(page)) {
                // Wait 1 second to make sure the date has changed.
                Sleeper.sleepSeconds(1);
                resp.setDateHeader("Last-Modified", System.currentTimeMillis());
                out.println("<h1>Modified test page 3 (meta + content)</h1>");
                out.println("<p>This page content always changes because of "
                        + "this random number: " + System.currentTimeMillis()
                        + "</p><p>The Last-Modified HTTP response value is "
                        + "always the current date (so it keeps changing)."
                        + "</p>");
            }
        }
    }

    class CanonicalTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {
            String type = req.getParameter("type");
            String canonicalURL = "http://localhost:" + req.getLocalPort()
                        + "/test?case=canonical";

            if ("httpheader".equals(type)) {
                resp.setHeader("Link", "<" + canonicalURL
                        + ">; rel=\"canonical\"");
                out.println("<body><p>Canonical URL in HTTP header.</p>");
            } else if ("linkrel".equals(type)) {
                out.println("<head>");
                out.println("<link rel=\"canonical\" ");
                out.println("href=\"" + canonicalURL + "\" />");
                out.println("</head>");
                out.println("<body><p>Canonical URL in HTML &lt;head&gt;.</p>");
            } else {
                out.println("<body><p>Canonical page</p>");
            }
            out.println(
                    "<h1>Handling of (non)canonical URLs</h1>"
                  + "<p>The links below are pointing to pages that should "
                  + "be considered copies of this page when accessed "
                  + "without URL parameters.</p>"
                  + "<ul>"
                  + "<li><a href=\"?case=canonical&type=httpheader\">"
                  + "HTTP Header</a></li>"
                  + "<li><a href=\"?case=canonical&type=linkrel\">"
                  + "link rel</a></li>"
                  + "</ul>"
                  + "</body>"
            );
        }
    }

    // Canonical points to a page that redirects back to canonical
    class CanonicalRedirectLoopTestCase extends HtmlTestCase {
        private final MutableInt count = new MutableInt();

        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            if (count.intValue() == 10) {
                resp.sendError(HttpStatus.SC_INTERNAL_SERVER_ERROR,
                        "Too many canonicals + redirects (loop).");
                count.setValue(0);
                return;
            }

            count.increment();
            String type = req.getParameter("type");
            String baseURL = "http://localhost:" + req.getLocalPort()
                    + "/test?case=canonRedirLoop";

            if ("canonical".equals(type)) {
LOG.warn(">>> Canonical requested, which points to redirect.");
                resp.setHeader("Link",
                        "<" + baseURL + "&type=redirect>; rel=\"canonical\"");
                out.println("<h1>Canonical-redirect circular reference.</h1>"
                        + "<p>This page has a canonical URL in the HTTP header "
                        + "that points to a page that redirects back to this "
                        + "one (loop). The crawler should be smart enough "
                        + "to pick one and not enter in an infite loop.</p>");
            } else {
LOG.warn(">>> Redirect requested, which points to canonical.");
                resp.sendRedirect(baseURL + "&type=canonical");
            }
        }
    }


    class SpecialURLTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            String page = req.getParameter("page");

            out.println("<h1>Special URLs test page " + page + "</h1>");

            if (StringUtils.isBlank(page)) {
                out.println("<p>This page contains URLs with special characters "
                        + "that may potentially cause issues if not handled "
                        + "properly.</p>");
                out.println("<p>"
                      + "<a href=\"?case=specialURLs&page=1&param=a%2Fb\">"
                      + "Slashes Already Escaped</a><br>"
                      + "<a href=\"/test/co,ma.html?case=specialURLs"
                      + "&page=2&param=a,b&par,am=c,,d\">Comas</a><br>"
                      + "<a href=\"/test/spa ce.html?case=specialURLs"
                      + "&page=2&param=a b&par am=c d\">Spaces</a><br>"
                      + "</p>"
                );
            } else if ("1".equals(page)) {
                out.println("<p>This is a page accessed with a URL that "
                        + "had slashes already escaped in it.</p>");
            } else if ("2".equals(page)) {
                out.println("<p>This is a page accessed with a URL that "
                        + "had unescaped comas in it.</p>");
            } else if ("3".equals(page)) {
                out.println("<p>This is a page accessed with a URL that "
                        + "had unescaped spaces in it.</p>");
            }
            if (StringUtils.isNotBlank(page)) {
                out.println("<p>URL:<xmp>");
                StringBuffer requestURL = req.getRequestURL();
                String queryString = req.getQueryString();
                if (queryString == null) {
                    out.println(requestURL.toString());
                } else {
                    out.println(requestURL.append(
                            '?').append(queryString).toString());
                }
                out.println("</xmp></p>");
            }
        }
    }

    // Test case for https://github.com/Norconex/collector-http/issues/232
    class ScriptTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {
            boolean isScript = Boolean.valueOf(req.getParameter("script"));
            if (!isScript) {
                out.println("<h1>Page with a script tag</h1>");
                out.println("<script src=\"/test?case=script&script=true\">"
                    + "THIS_MUST_BE_STRIPPED, but src URL must be crawled"
                    + "</script>");
                out.println("<script>THIS_MUST_BE_STRIPPED</script>");
                out.println("View the source to see &lt;script&gt; tags");
            } else {
                out.println("<h1>The Script page</h1>");
                out.println("This must be crawled.");
            }
        }
    }

    // Test case for https://github.com/Norconex/collector-http/issues/313
    class ZeroLengthTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp) throws Exception {
            // returns nothing (empty)
        }
        @Override
        protected void doTestCase(
                HttpServletRequest req, HttpServletResponse resp,
                PrintWriter out) throws Exception {
            // returns nothing (empty)
        }
    }

    // child pages return after 1 minute when accessed for second time
    class TimeoutTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {
            String page = req.getParameter("page");
            String token = req.getParameter("token");

            if (StringUtils.isBlank(page)) {
                if (StringUtils.isNotBlank(token)) {
                    String pageToken = "page-" + page + "-" + token;
                    if (tokens.contains(pageToken)) {
                        tokens.remove(pageToken);
                        Sleeper.sleepSeconds(10);
                    } else {
                        tokens.add(pageToken);
                    }
                }
                out.println("<h1>Timeout test main page</h1>");
                out.println("<p>If provided with a 'token' parameter, this "
                    + "page takes 10 seconds to return to test "
                    + "timeouts, the 2 links below should return right away "
                    + "and have a modified content each time accessed : "
                    + "<ul>"
                    + "<li><a href=\"?case=timeout&page=1\">"
                    + "Timeout child page 1</a></li>"
                    + "<li><a href=\"?case=timeout&page=2\">"
                    + "Timeout child page 2</a></li>"
                    + "</ul>");
            } else {
                Sleeper.sleepMillis(10);
                out.println("<h1>Timeout test child page " + page + "</h1>");
                out.println("<p>This page content is never the same.</p>"
                        + "<p>Salt: " + System.currentTimeMillis() + "</p><p>"
                        + "Contrary to main page, it should return right "
                        + "away</p>");
            }
        }
    }

    class IFrameTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            String page = req.getParameter("page");

            out.println("<h1>IFrame test page " + page+ "</h1>");

            if (StringUtils.isBlank(page)) {
                out.println("<p>This page includes 2 &lt;iframe&gt; tags.</p>");
                out.println("<iframe src=\"?case=iframe&amp;page=1\">"
                        + "</iframe>");
                out.println("<iframe src=\"?case=iframe&amp;page=2\">"
                        + "Some iframe content here."
                        + "</iframe>");
            } else if ("1".equals(page)) {
                out.println("<p>This is iframe 1.</p>");
            } else if ("2".equals(page)) {
                out.println("<p>This is iframe 2.</p>");
            }
            if (StringUtils.isNotBlank(page)) {
                out.println("<p>URL:<xmp>");
                StringBuffer requestURL = req.getRequestURL();
                String queryString = req.getQueryString();
                if (queryString == null) {
                    out.println(requestURL.toString());
                } else {
                    out.println(requestURL.append(
                            '?').append(queryString).toString());
                }
                out.println("</xmp></p>");
            }
        }
    }

    class MergeTestCase extends HtmlTestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp, PrintWriter out) throws Exception {

            String page = req.getParameter("page");

            if (StringUtils.isBlank(page)) {
                String html =
                          "<h1>Merge test page: Source page</h1>\n"
                        + "<head>\n"
                        + "<meta name=\"Sfield1\" content=\"Svalue1\">\n"
                        + "<meta name=\"Sfield2\" content=\"Svalue2\">\n"
                        + "<meta name=\"Sfield3\" content=\"Svalue3\">\n"
                        + "<head>\n"
                        + "<body>\n"
                        + "<p>This source page includes metadata to test"
                        + "merging.</p>\n"
                        + "<a href=\"?case=merge&amp;page=cat\">cat</a>\n"
                        + "<a href=\"?case=merge&amp;page=dog\">dog</a>\n"
                        + "</body>\n";
                out.println(html);
            } else {
                out.println("<h1>Merge test page: Target page: "
                        + page + "</h1>");
                String html =
                          "<h1>Merge test page: Source page: " +page+ "</h1>\n"
                        + "<head>\n"
                        + "<meta name=\"T1" + page
                        + "\" content=\"tvalue1-" + page + "\">\n"
                        + "<meta name=\"T2" + page
                        + "\" content=\"tvalue2-" + page + "\">\n"
                        + "<meta name=\"T3" + page
                        + "\" content=\"tvalue3-" + page + "\">\n"
                        + "<head>\n"
                        + "<body>\n"
                        + "<p>This target page " + page
                        + " includes metadata to test merging.</p>\n"
                        + "</body>\n";
                out.println(html);
            }
        }
    }

    class ContentTypeCharsetTestCase implements ITestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp) throws Exception {
            resp.setContentType("application/javascript");
            resp.setCharacterEncoding("Big5");
            String out = "<html style=\"font-family:Arial, "
                     + "Helvetica, sans-serif;\">"
                     + "<head><title>ContentType + Charset ☺☻"
                     + "</title></head>"
                     + "<body>This page returns the Content-Type as "
                     + "\"application/javascript; charset=Big5\" "
                     + "while in reality it is \"text/html; charset=UTF-8\"."
                     + "Éléphant à noël. ☺☻"
                     + "</body>"
                     + "</html>";
            resp.getOutputStream().write(out.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * The second time the sitemap has 1 less URL and that URL no longer
     * exists.
     */
    class SitemapTestCase implements ITestCase {
        @Override
        public void doTestCase(HttpServletRequest req,
                HttpServletResponse resp) throws Exception {

            int page = NumberUtils.toInt(req.getParameter("page"), -1);
            String token = req.getParameter("token");

            // if page is blank, the request is for the sitemap
            if (page == -1) {
                String baseLocURL = "http://localhost:" + req.getLocalPort()
                       + "/test?case=sitemap&amp;token=" + token + "&amp;page=";
                Map<String, String> vars = new HashMap<>();
                vars.put("loc1", baseLocURL + 1);
                vars.put("loc2", baseLocURL + 2);
                if (!tokens.contains(token)) {
                    vars.put("loc3", baseLocURL + 3);
                    tokens.add(token);
                } else {
                    vars.put("loc3", baseLocURL + 33);
                    tokens.remove(token);
                }
                String xml = IOUtils.toString(getClass().getResourceAsStream(
                        "sitemap.xml"), StandardCharsets.UTF_8);
                xml = StringSubstitutor.replace(xml, vars);
                resp.setContentType("application/xml");
                resp.setCharacterEncoding("UTF-8");
                resp.getWriter().println(xml);
            } else {
                resp.setContentType("text/html");
                resp.setCharacterEncoding("UTF-8");
                PrintWriter out = resp.getWriter();

                if (page < 3) {
                    out.println("<h1>Sitemap permanent page " + page + "</h1>");
                    out.println("<p>This page should always be there.</p>");
                } else if (page == 3) {
                    if (tokens.contains(token)) {
                        out.println("<h1>Sitemap temp page " + page + "</h1>");
                        out.println("<p>This page should be there the first "
                                + "time the site is crawled only.</p>");
                    } else {
                        resp.sendError(HttpStatus.SC_NOT_FOUND,
                                "Not found (so they say)");
                    }

                } else if (page == 33) {
                    out.println("<h1>Sitemap new page " + page + "</h1>");
                    out.println("<p>This page should be there the second "
                            + "time the site is crawled only.</p>");
                }
            }
        }
    }
}
