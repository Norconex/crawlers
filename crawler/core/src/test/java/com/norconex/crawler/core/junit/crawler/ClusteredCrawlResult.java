/* Copyright 2025 Norconex Inc.
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
package com.norconex.crawler.core.junit.crawler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.norconex.commons.lang.map.Properties;

import lombok.Data;

/**
 * Standardized facade to crawler execution on a cluster, for testing.
 */
@Data
public class ClusteredCrawlResult {

    @Data
    public static class CrawlNode {
        private final String stdout;
        private final String stderr;
        private final int exitCode;

        public String fileContent(String relPath) {
            return null;
        }
    }

    private List<CrawlNode> nodes = new ArrayList<>();

    private Map<String, Properties> caches = new HashMap<>();

}
