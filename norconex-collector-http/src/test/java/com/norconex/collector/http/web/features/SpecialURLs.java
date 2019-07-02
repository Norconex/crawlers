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
package com.norconex.collector.http.web.features;

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;

import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core.impl.MemoryCommitter;

/**
 * Test that special characters in URLs are handled properly.
 * @author Pascal Essiembre
 */
public class SpecialURLs extends AbstractTestFeature {

    @Override
    public void doHtmlService(
            HttpServletRequest req, PrintWriter out) throws Exception {
        String page = req.getParameter("page");

        out.println("<h1>Special URLs test page " + page + "</h1>");

        if (StringUtils.isBlank(page)) {
            out.println("<p>This page contains URLs with special characters "
                    + "that may potentially cause issues if not handled "
                    + "properly.</p>");
            out.println("<p>"
                  + "<a href=\"" + req.getRequestURL()
                  + "?page=1&param=a%2Fb\">"
                  + "Slashes Already Escaped</a><br>"
                  + "<a href=\"" + req.getRequestURL() + "/test/co,ma.html"
                  + "?page=2&param=a,b&par,am=c,,d\">Commas</a><br>"
                  + "<a href=\"" + req.getRequestURL() + "/test/spa ce.html"
                  + "?page=3&param=a b&par am=c d\">Spaces</a><br>"
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

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        assertListSize("document", committer.getAddOperations(), 4);
    }
}
