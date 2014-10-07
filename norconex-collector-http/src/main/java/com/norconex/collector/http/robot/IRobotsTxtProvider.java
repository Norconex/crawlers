/* Copyright 2010-2014 Norconex Inc.
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
package com.norconex.collector.http.robot;

import org.apache.http.client.HttpClient;

/**
 * Given a URL, extract any "robots.txt" rules.
 * @author Pascal Essiembre
 */
public interface IRobotsTxtProvider {

    /**
     * Gets robots.txt rules.
     * This method signature changed in 1.3 to include the userAgent.
     * @param httpClient the http client to grab robots.txt
     * @param url the URL to derive the robots.txt from
     * @param userAgent the User-Agent to match ourselves with the robot rules
     * @return robots.txt rules
     */
    RobotsTxt getRobotsTxt(HttpClient httpClient, String url, String userAgent);
    
}
