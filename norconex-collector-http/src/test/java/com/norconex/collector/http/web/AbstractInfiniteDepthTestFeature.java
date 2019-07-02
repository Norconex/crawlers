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

import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;

import com.norconex.commons.lang.map.Properties;

/**
 * An infinite depth web site that can be a base class for several tests.
 * @author Pascal Essiembre
 */
public abstract class AbstractInfiniteDepthTestFeature
        extends AbstractTestFeature {

    @Override
    public void doHtmlService(
            HttpServletRequest req, PrintWriter out) throws Exception {
        Properties params = new Properties();
        params.loadFromMap(req.getParameterMap());
        int depth = params.getInteger("depth", 0);
        int prevDepth = depth - 1;
        int nextDepth = depth + 1;
        out.println("<h1>" + getPath() + " test page</h1>");
        out.println("<p>You can click, click, click, and click again.</p>");
        if (prevDepth >= 0) {
            out.println("<a href=\"" + req.getRequestURL()
                    + "?depth=" + prevDepth + "\">Previous depth is "
                    + prevDepth + "</a><br><br>");
        }
        out.println("<b>This page is of depth: " + depth + "</b><br><br>");
        out.println("<a href=\"" + req.getRequestURL()
                + "?depth=" + nextDepth + "\">Next depth is "
                + nextDepth + "</a>");

    }
}
