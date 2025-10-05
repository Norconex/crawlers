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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.norconex.crawler.core.cluster.impl.infinispan.CacheNames;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;
import com.norconex.crawler.core.junit.crawler.PathListParser.FsEntry;
import com.norconex.crawler.core.util.SerialUtil;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * Standardized facade to crawler execution on a cluster, for testing.
 */
@Data
@Accessors(chain = true)
public class ClusteredCrawlOuput {

    @Data
    @Accessors(chain = true)
    public static class CrawlNode {
        private String name;
        private String stdout;
        private String stderr;
        private int exitCode;
        private FsEntry workdirFiles;
    }

    private final List<CrawlNode> nodes;

    private final Map<String, List<JsonNode>> caches = new HashMap<>();

    private final StringBuilder msg = new StringBuilder();

    public StepRecord getPipeResult() {
        var jsonObjs = caches.get(CacheNames.PIPE_CURRENT_STEP);
        if (jsonObjs == null || jsonObjs.isEmpty()) {
            return null;
        }
        // when testing there should only be one entry so grab first.
        return SerialUtil.fromJson(jsonObjs.get(0).get("object"),
                StepRecord.class);
    }

    // output.getNodes().forEach(res -> {
    //     System.err.println(
    //             "XXX Exit code: %s\nSTDOUT:\n%sSTDERR:\n".formatted(
    //                     res.getExitCode(), res.getStdout(),
    //                     res.getStderr()));
    // });
    // System.err.println("XXX WORKDIR CONTENT:");
    // output.getNodes().get(0).getWorkdirFiles().printTree(4);

    // output.getCaches().forEach((k, v) -> {
    //     System.err.println("XXX CACHES: " + k + " ==> " + v);

    // });

    //XXX CACHES: pipeCurrentStep ==> [{"id":"cs-01K6S8CDAQNTTKR8M30PH44QBE:test-completion","object":{"pipelineId":"test-completion","stepId":"step3","updatedAt":1759636770217,"status":"COMPLETED","runId":"cr-01K6S8CD8HFKSQ2ST7RHPCQW0B"}}]
}
