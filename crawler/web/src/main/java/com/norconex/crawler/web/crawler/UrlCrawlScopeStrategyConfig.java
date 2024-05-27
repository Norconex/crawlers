/* Copyright 2015-2024 Norconex Inc.
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
package com.norconex.crawler.web.crawler;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>By default a crawler will try to follow all links it discovers. You can
 * define your own filters to limit the scope of the pages being crawled.
 * When you have multiple URLs defined as start URLs, it can be tricky to
 * perform global filtering that apply to each URLs without causing
 * URL filtering conflicts.  This class offers an easy way to address
 * a frequent URL filtering need: to "stay on target". That is,
 * when following a page and extracting URLs found in it, make sure to
 * only keep URLs that are on the same site as the page URL we are on.
 * </p>
 * <p>
 * By default this class does not request to stay on a site.
 * </p>
 * @since 2.3.0
 */
//TODO make this an interface so developers can provide their own?
@Data
@Accessors(chain = true)
@SuppressWarnings("javadoc")
public class UrlCrawlScopeStrategyConfig {

    /**
     * Whether the crawler should always stay on the same domain name as
     * the domain for each URL specified as a start URL.  By default (false)
     * the crawler will try follow any discovered links not otherwise rejected
     * by other settings (like regular filtering rules you may have).
     * @param stayOnDomain <code>true</code> for the crawler to stay on domain
     * @return <code>true</code> if the crawler should stay on a domain
     */
    private boolean stayOnDomain;

    /**
     * Whether sub-domains are considered to be the same as a URL domain.
     * Only applicable when "stayOnDomain" is <code>true</code>.
     * @param includeSubdomains <code>true</code> to include sub-domains
     * @return <code>true</code> if including sub-domains
     * @since 2.9.0
     */
    private boolean includeSubdomains;

    /**
     * Whether the crawler should always stay on the same port as
     * the port for each URL specified as a start URL.  By default (false)
     * the crawler will try follow any discovered links not otherwise rejected
     * by other settings (like regular filtering rules you may have).
     * @param stayOnPort <code>true</code> for the crawler to stay on port
     * @return <code>true</code> if the crawler should stay on a port
     */
    private boolean stayOnPort;

    /**
     * Whether the crawler should always stay on the same protocol as
     * the protocol for each URL specified as a start URL.  By default (false)
     * the crawler will try follow any discovered links not otherwise rejected
     * by other settings (like regular filtering rules you may have).
     * @param stayOnProtocol
     *        <code>true</code> for the crawler to stay on protocol
     * @return <code>true</code> if the crawler should stay on protocol
     */
    private boolean stayOnProtocol = false;


    /**
     * Whether to limit crawling to entries found in an existing website
     * sitemap (do not go deeper). Only applies if a sitemap is present
     * for a website.
     * This option is similar to specifying a sitemap start URL with a
     * <code>maxDepth</code> of <code>1</code> with the difference that when
     * used with regular start URLs and no sitemap is detected, it will crawl
     * the corresponding website as if this option was set to
     * <code>false</code>.
     * Does not apply if sitemap resolution has been disabled in your
     * configuration.
     * Note that if <code>async</code> is <code>true</code>, you may get
     * a few soft rejections until they are identified in the sitemap.
     */
    private boolean stayOnSitemap;

}
