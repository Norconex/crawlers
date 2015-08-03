/* Copyright 2014-2015 Norconex Inc.
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;

import com.norconex.commons.lang.Sleeper;
import com.norconex.commons.lang.map.Properties;

/**
 * @author Pascal Essiembre
 *
 */
public class TestServlet extends HttpServlet {

    private static final long serialVersionUID = -4252570491708918968L;

    private final Map<String, ITestCase> testCases = new HashMap<>();
    
    private final List<String> tokens = new ArrayList<String>();
    
    /**
     * Constructor.
     */
    public TestServlet() {
        testCases.put("list", new ListTestCases());
        testCases.put("basic", new BasicTestCase());
        testCases.put("redirect", new RedirectTestCase());
        testCases.put("userAgent", new UserAgentTestCase());
        testCases.put("keepDownloads", new KeepDownloadedFilesTestCase());
        testCases.put("deletedFiles", new DeletedFilesTestCase());
        testCases.put("modifiedFiles", new ModifiedFilesTestCase());
        testCases.put("canonical", new CanonicalTestCase());
        testCases.put("specialURLs", new SpecialURLTestCase());
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
        public void doTestCase(HttpServletRequest req, 
                HttpServletResponse resp) throws Exception {
            resp.setContentType("text/html");
            resp.setCharacterEncoding(CharEncoding.UTF_8);
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
        public void doTestCase(HttpServletRequest req, 
                HttpServletResponse resp, PrintWriter out) throws Exception {
            Properties params = new Properties();
            params.load(req.getParameterMap());
            int depth = params.getInt("depth", 0);
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
    
    class UserAgentTestCase extends HtmlTestCase {
        public void doTestCase(HttpServletRequest req, 
                HttpServletResponse resp, PrintWriter out) throws Exception {
            String userAgent = req.getHeader("User-Agent");
            out.println("<h1>User Agent test page</h1>");
            out.println("The user agent is: " + userAgent);
        }
    }

    class KeepDownloadedFilesTestCase extends HtmlTestCase {
        public void doTestCase(HttpServletRequest req, 
                HttpServletResponse resp, PrintWriter out) throws Exception {

            out.println("<h1>Keep downloaded files == true</h1>");
            out.println("<b>This</b> file <i>must</i> be saved as is, "
                    + "with this <span>formatting</span>");
        }
    }

    class DeletedFilesTestCase extends HtmlTestCase {
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
    
    class SpecialURLTestCase extends HtmlTestCase {
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
}
