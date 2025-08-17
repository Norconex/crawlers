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
package com.norconex.crawler.core.cluster.impl.infinispan;

public final class StepReducers {

    private StepReducers() {
    }
    //
    //    static PipelineStepRecord defaultReduce(
    //            Map<String, PipelineStepRecord> workerStatuses,
    //            List<String> nodeNames) {
    //        var reducedStatus = PipelineStatus.PENDING;
    //        while (!reducedStatus.isTerminal()) {
    //            var allPresent = workerStatuses.keySet().containsAll(nodeNames);
    //            Bag<PipelineStatus> statuses = new HashBag<>();
    //            workerStatuses.values().forEach(r -> statuses.add(r.getStatus()));
    //            var allTerminal = allPresent
    //                    && statuses.stream().allMatch(PipelineStatus::isTerminal);
    //            if (allTerminal) {
    //                LOG.info(
    //                        "Distributed step {} node statuses: {} COMPLETED, {} FAILED, {} STOPPED",
    //                        step.getId(),
    //                        statuses.getCount(PipelineStatus.COMPLETED),
    //                        statuses.getCount(PipelineStatus.FAILED),
    //                        statuses.getCount(PipelineStatus.STOPPED));
    //                if (statuses.getCount(PipelineStatus.COMPLETED) > 0) {
    //                    reducedStatus = PipelineStatus.COMPLETED;
    //                } else if (statuses.getCount(PipelineStatus.STOPPED) > 0) {
    //                    reducedStatus = PipelineStatus.STOPPED;
    //                } else {
    //                    reducedStatus = PipelineStatus.FAILED;
    //                }
    //            } else if (System.currentTimeMillis() - start > TIMEOUT_MS) {
    //                LOG.warn(
    //                        "Distributed step {} timed out waiting for all workers. Present: {}/{}",
    //                        step.getId(), workerStatuses.size(), nodeNames.size());
    //                if (statuses.getCount(PipelineStatus.COMPLETED) > 0) {
    //                    reducedStatus = PipelineStatus.COMPLETED;
    //                } else {
    //                    reducedStatus = PipelineStatus.FAILED;
    //                }
    //            } else {
    //                Sleeper.sleepMillis(250);
    //            }
    //        }
    //        return reducedStatus;
    //    }
}
