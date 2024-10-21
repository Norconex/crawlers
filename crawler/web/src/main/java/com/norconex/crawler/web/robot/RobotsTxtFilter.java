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
package com.norconex.crawler.web.robot;

import com.norconex.crawler.core.tasks.crawl.operations.filter.OnMatch;
import com.norconex.crawler.core.tasks.crawl.operations.filter.OnMatchFilter;
import com.norconex.crawler.core.tasks.crawl.operations.filter.ReferenceFilter;

/**
 * Holds a robots.txt rule. The {@link #getOnMatch()} method
 * indicate whether the rule is a "Disallow" or "Allow".
 * A "Disallow" rule is represented by
 * {@link OnMatch#EXCLUDE} whereas "Allow" is represented by
 * {@link OnMatch#INCLUDE}.
 * @since 2.4.0
 */
public interface RobotsTxtFilter extends ReferenceFilter, OnMatchFilter {

    String getPath();
}
