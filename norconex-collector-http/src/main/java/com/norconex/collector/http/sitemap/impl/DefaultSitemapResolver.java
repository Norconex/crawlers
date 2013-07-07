/* Copyright 2010-2013 Norconex Inc.
 * 
 * This file is part of Norconex HTTP Collector.
 * 
 * Norconex HTTP Collector is free software: you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Norconex HTTP Collector is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with Norconex HTTP Collector. If not, 
 * see <http://www.gnu.org/licenses/>.
 */
package com.norconex.collector.http.sitemap.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.sitemap.ISitemapsResolver;
import com.norconex.collector.http.sitemap.SitemapURLStore;
import com.norconex.commons.lang.config.IXMLConfigurable;

/**
 * <p>
 * Default implementation of {@link ISitemapsResolver}.  For any given URL
 * this class will look in three different places to locate sitemaps:
 * </p>
 * <ul>
 *   <li>Sitemap locations explicitly provided via configuration (or setter
 *       method on this class).</li>
 *   <li>The root-level of a URL (e.g. http://example.com/sitemap.xml)</li>
 *   <li>Any sitemaps defined in robots.txt
 *       (automatically passed as arguments to this class if robots.txt are
 *        not ignored)</li>
 * </ul>
 * <p>
 * By default basic limitations imposed from the sitemap specifications are
 * respected.  Setting lenient to <code>true</code> changes that.  For 
 * example, sitemap.xml files defined in a sub-directory applies only
 * to URLs found in that sub-directory or its children.  Being lenient 
 * no longer honors that restriction.
 * </p>
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;sitemap ignore="false" lenient="false"
 *     class="com.norconex.collector.http.handler.DefaultSitemapProvider"&gt;
 *     &lt;location&gt;(optional location of sitemap.xml)&lt;/location&gt;
 *     (... repeat location tag as needed ...)
 *  &lt;/sitemap&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultSitemapResolver 
        implements ISitemapsResolver, IXMLConfigurable {

    private static final long serialVersionUID = 4047819847150159618L;

    private static final Logger LOG = LogManager.getLogger(
            DefaultSitemapResolver.class);
    
    private String[] sitemapLocations;

    @Override
    public void resolveSitemaps(DefaultHttpClient httpClient, String urlRoot,
            String[] robotsTxtLocations, SitemapURLStore sitemapURLStore) {
        // TODO implement me
        
    }

    public String[] getSitemapLocations() {
        return sitemapLocations;
    }
    public void setSitemapLocations(String[] sitemapLocations) {
        this.sitemapLocations = sitemapLocations;
    }
    
//    @Override
//    public SitemapURLStore getSitemap(DefaultHttpClient httpClient, String url,
//            String[] robotsTxtLocations) {
//        String baseURL = getBaseURL(url);
//        SitemapURLStore sitemap = sitemapCache.get(baseURL);
//        if (sitemap != null) {
//            return sitemap;
//        }
//        
//        
//        
//        
//        
//        String userAgent = ((String) httpClient.getParams().getParameter(
//                CoreProtocolPNames.USER_AGENT)).toLowerCase();
//        String robotsURL = baseURL + "/robots.txt";
//        HttpGet method = new HttpGet(robotsURL);
//        List<String> sitemapLocations = new ArrayList<String>();
//        List<IURLFilter> filters = new ArrayList<IURLFilter>();
//        MutableFloat crawlDelay = 
//                new MutableFloat(RobotsTxt.UNSPECIFIED_CRAWL_DELAY);
//        try {
//            HttpResponse response = httpClient.execute(method);
//            InputStreamReader isr = 
//                    new InputStreamReader(response.getEntity().getContent());
//            BufferedReader br = new BufferedReader(isr);
//            boolean agentAlreadyMatched = false;
//            boolean doneWithAgent = false;
//            String line;
//            while ((line = br.readLine()) != null) {
//                String key = line.replaceFirst("(.*?)(:.*)", "$1").trim();
//                String value = line.replaceFirst("(.*?:)(.*)", "$2").trim();
//                if ("sitemap".equalsIgnoreCase(key)) {
//                    sitemapLocations.add(value);
//                }
//                if (!doneWithAgent) {
//                    if ("user-agent".equalsIgnoreCase(key)) {
//                        if (matchesUserAgent(userAgent, value)) {
//                            agentAlreadyMatched = true;
//                        } else if (agentAlreadyMatched) {
//                            doneWithAgent = true;
//                        }
//                    }
//                    if (agentAlreadyMatched) {
//                        parseAgentLines(
//                                baseURL, filters, crawlDelay, key, value);
//                    }
//                }
//            }
//            isr.close();
//        } catch (Exception e) {
//            if (LOG.isDebugEnabled()) {
//                LOG.info("Not able to obtain robots.txt at: " + robotsURL, e);
//            } else {
//                LOG.info("Not able to obtain robots.txt at: " + robotsURL);
//            }
//        }
//        
//        sitemap = new SitemapURLStore(
//                filters.toArray(new IURLFilter[]{}));
//        sitemapCache.put(baseURL, sitemap);
//        return sitemap;
//    }

    @Override
    public void loadFromXML(Reader in) throws IOException {
        // TODO Auto-generated method stub
        
    }

    @Override
    public void saveToXML(Writer out) throws IOException {
        // TODO Auto-generated method stub
        
    }

}
