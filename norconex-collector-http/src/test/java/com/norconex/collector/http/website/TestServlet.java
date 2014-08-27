/**
 * 
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
        testCases.put("depth", new DepthTestCase());
        
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
    class DepthTestCase extends HtmlTestCase {
        public void doTestCase(HttpServletRequest req, 
                HttpServletResponse resp, PrintWriter out) throws Exception {
            Properties params = new Properties();
            params.load(req.getParameterMap());

            int depth = params.getInt("depth", 0);
            int prevDepth = depth - 1;
            int nextDepth = depth + 1;
            
            out.println("<h1>Dept test case:</h1>");
            
            if (prevDepth >= 0) {
                out.println("<a href=\"?case=depth&depth=" + prevDepth
                        + "\">Previous depth is " + prevDepth + "</a><p />");
            }
            out.println("<b>This page is of depth: " + depth + "</b><p />");
            out.println("<a href=\"?case=depth&depth=" + nextDepth
                    + "\">Next depth is " + nextDepth + "</a>");
            
        }
    }
}
