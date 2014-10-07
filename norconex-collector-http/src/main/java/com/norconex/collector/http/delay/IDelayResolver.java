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
package com.norconex.collector.http.delay;

import com.norconex.collector.http.robot.RobotsTxt;

/**
 * Resolves and creates intentional "delays" to increase document download
 * time intervals. This interface
 * does not dictate how delays are resolved.  It is left to implementors to
 * put in place their own strategy (e.g. pause all threads, delay 
 * multiple crawls on the same website domain only, etc).
 * Try to be "nice" to the web sites you crawl.
 * @author Pascal Essiembre
 */
public interface IDelayResolver {

    /**
     * Delay crawling activities (if applicable).
     * @param robotsTxt robots.txt instance (if applicable)
     * @param url the URL being crawled
     */
    void delay(RobotsTxt robotsTxt, String url);
}
