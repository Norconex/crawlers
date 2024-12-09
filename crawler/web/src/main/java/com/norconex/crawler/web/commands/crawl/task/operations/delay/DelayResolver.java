/* Copyright 2010-2024 Norconex Inc.
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
package com.norconex.crawler.web.commands.crawl.task.operations.delay;

import com.norconex.crawler.web.commands.crawl.task.operations.robot.RobotsTxt;

/**
 * Resolves and creates intentional "delays" to increase document download
 * time intervals. This interface
 * does not dictate how delays are resolved.  It is left to implementors to
 * put in place their own strategy (e.g. pause all threads, delay
 * multiple crawls on the same website domain only, etc).
 * Try to be "nice" to the web sites you crawl.
 */
public interface DelayResolver {

    /**
     * Delay crawling activities (if applicable).
     * @param robotsTxt robots.txt instance (if applicable)
     * @param url the URL being crawled
     */
    void delay(RobotsTxt robotsTxt, String url);
}
