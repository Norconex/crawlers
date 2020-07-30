/* Copyright 2010-2019 Norconex Inc.
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
package com.norconex.collector.http.robot;

import com.norconex.collector.http.fetch.HttpFetchClient;

/**
 * Given a URL, extract any "robots.txt" rules. Implementations are expected
 * to cache existing robots.txt instances or, cache the fact none was found,
 * for the duration of a crawl session so no attempt to re-download it is made.
 * @author Pascal Essiembre
 */
public interface IRobotsTxtProvider {

    /**
     * Gets robots.txt rules.
     * This method signature changed in 1.3 to include the userAgent.
     * @param fetchClient http fetcher executor to grab robots.txt
     * @param url the URL to derive the robots.txt from
//     * @param userAgent the User-Agent to match ourselves with the robot rules
     * @return robots.txt rules
     */
    RobotsTxt getRobotsTxt(HttpFetchClient fetchClient, String url);
//    RobotsTxt getRobotsTxt(HttpFetcherExecutor fetcher, String url, String userAgent);

}
