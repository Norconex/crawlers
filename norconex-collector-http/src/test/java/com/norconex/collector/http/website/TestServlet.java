/* Copyright 2014 Norconex Inc.
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
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.CharEncoding;
import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.map.Properties;

/**
 * @author Pascal Essiembre
 *
 */
public class TestServlet extends HttpServlet {

    private static final long serialVersionUID = -4252570491708918968L;

    private final Map<String, TestCase> testCases = new HashMap<>();
    
    /**
     * Constructor.
     */
    public TestServlet() {
        testCases.put("list", new ListTestCases());
        testCases.put("basic", new BasicTestCase());
        testCases.put("redirect", new RedirectTestCase());
        testCases.put("userAgent", new UserAgentTestCase());
        testCases.put("keepDownloads", new KeepDownloadedFilesTestCase());
    }
    
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String testCaseKey = StringUtils.trimToNull(req.getParameter("case"));
        if (StringUtils.isBlank(testCaseKey)) {
            testCaseKey = "list";
        }
        TestCase testCase = testCases.get(testCaseKey);
        if (testCase == null) {
            testCase = testCases.get("list");
        }
        try {
            testCase.doTestCase(req, resp);
            resp.flushBuffer();
        } catch (Exception e) {
            e.printStackTrace(resp.getWriter());
        }
    }

    interface TestCase {
        void doTestCase(HttpServletRequest req, 
                HttpServletResponse resp) throws Exception;
    }
    abstract class HtmlTestCase implements TestCase {
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
            out.println("<h1>Invalid or no test case specified.</h1>");
            out.println("Available test cases are:");
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
            out.println("<p>Tests: depth, validMetadata</p>");
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

    
//    class InfinitTestCase extends HtmlTestCase {
//        public void doTestCase(HttpServletRequest req, 
//                HttpServletResponse resp, PrintWriter out) throws Exception {
//
//            int count = NumberUtils.toInt(req.getParameter("count"), 0);
//            count++;
//            
//            out.println("<h1>To infinity and beyond!</h1>");
//            out.println("Useful test case when long-running crawls are"
//                    + "desired.");
//            out.println("<a href=\"\">" +  + "</a>");
//        }
//    }

}
