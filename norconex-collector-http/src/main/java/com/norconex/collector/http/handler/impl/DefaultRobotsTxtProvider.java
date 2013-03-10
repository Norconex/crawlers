package com.norconex.collector.http.handler.impl;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.params.HttpMethodParams;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.http.filter.IURLFilter;
import com.norconex.collector.http.filter.OnMatch;
import com.norconex.collector.http.filter.impl.RegexURLFilter;
import com.norconex.collector.http.handler.IRobotsTxtProvider;
import com.norconex.collector.http.robot.RobotsTxt;

/**
 * Default implementation of {@link IRobotsTxtProvider}.  
 * <p>
 * XML configuration usage (not required since default):
 * </p>
 * <pre>
 *  &lt;robotsTxt ignore="false" class="com.norconex.collector.http.handler.DefaultHttpClientInitializer"/&gt;
 * </pre>
 * @author Pascal Essiembre
 */
public class DefaultRobotsTxtProvider implements IRobotsTxtProvider {

    private static final long serialVersionUID = 1459917072724725590L;
    private static final Logger LOG = LogManager.getLogger(
            DefaultRobotsTxtProvider.class);
    
    private Map<String, RobotsTxt> robotsTxtCache =
            new HashMap<String, RobotsTxt>();

    @Override
    synchronized public RobotsTxt getRobotsTxt(
            HttpClient httpClient, String url) {
        String baseURL = getBaseURL(url);
        RobotsTxt robotsTxt = robotsTxtCache.get(baseURL);
        if (robotsTxt != null) {
            return robotsTxt;
        }
        
        String userAgent = ((String) httpClient.getParams().getParameter(
                HttpMethodParams.USER_AGENT)).toLowerCase();
        String robotsURL = baseURL + "/robots.txt";
        GetMethod method = new GetMethod(robotsURL);
        List<IURLFilter> filters = 
                new ArrayList<IURLFilter>();
        float crawlDelay = RobotsTxt.UNSPECIFIED_CRAWL_DELAY;
        try {
            httpClient.executeMethod(method);
            InputStreamReader isr = 
                    new InputStreamReader(method.getResponseBodyAsStream());
            BufferedReader br = new BufferedReader(isr);
            boolean agentMatched = false;
            String line;
            while ((line = br.readLine()) != null) {
                String key = line.replaceFirst("(.*?)(:.*)", "$1").trim();
                String value = line.replaceFirst("(.*?:)(.*)", "$2").trim();
                if ("user-agent".equalsIgnoreCase(key)) {
                    if ("*".equals(value) || userAgent.contains(
                            value.toLowerCase())) {
                        agentMatched = true;
                    } else if (agentMatched) {
                        break;
                    }
                }
                if (agentMatched) {
                    if ("disallow".equalsIgnoreCase(key)) {
                        filters.add(buildURLFilter(
                        		baseURL, value, OnMatch.EXCLUDE));
                    } else if ("allow".equalsIgnoreCase(key)) {
                        filters.add(buildURLFilter(
                        		baseURL, value, OnMatch.INCLUDE));
                    } else if ("crawl-delay".equalsIgnoreCase(key)) {
                        crawlDelay = NumberUtils.toFloat(value, crawlDelay);
                    } else if ("sitemap".equalsIgnoreCase(key)) {
                        //TODO implement me.
                        LOG.warn("Sitemap in robots.txt encountered. "
                               + "CURRENTLY NOT SUPPORTED.");
                    }
                }
            }
            isr.close();
        } catch (Exception e) {
            if (LOG.isDebugEnabled()) {
                LOG.info("Not able to obtain robots.txt at: " + robotsURL, e);
            } else {
                LOG.info("Not able to obtain robots.txt at: " + robotsURL);
            }
        }
        
        
        
        robotsTxt = new RobotsTxt(
                filters.toArray(new IURLFilter[]{}), crawlDelay);
        robotsTxtCache.put(baseURL, robotsTxt);
        return robotsTxt;
    }
    
    
    
    private String getBaseURL(String url) {
        String baseURL = url.replaceFirst("(.*?://.*?/)(.*)", "$1");
        if (baseURL.endsWith("/")) {
            baseURL = StringUtils.removeEnd(baseURL, "/");
        }
        return baseURL;
    }
    
    private IURLFilter buildURLFilter(
            String baseURL, final String path, final OnMatch onMatch) {
        String regex = path;
        regex = regex.replaceAll("\\*", ".*");
        if (!regex.endsWith("$")) {
            regex += ".*";
        }
        regex = baseURL + regex;
        RegexURLFilter filter = new RegexURLFilter(regex, onMatch, false) {
            private static final long serialVersionUID = -5051322223143577684L;
            @Override
            public String toString() {
                return "Robots.txt (" 
                		+ (onMatch == OnMatch.INCLUDE ? "Allow:" : "Disallow:")
                        + path + ")";
            }
        };
        return filter;
    }
}
