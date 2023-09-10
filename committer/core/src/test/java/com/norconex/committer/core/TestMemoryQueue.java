/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.committer.core;

import java.util.ArrayList;
import java.util.List;

import com.norconex.committer.core.batch.BatchConsumer;
import com.norconex.committer.core.batch.queue.CommitterQueue;
import com.norconex.committer.core.batch.queue.CommitterQueueException;

import lombok.EqualsAndHashCode;
import lombok.ToString;

@ToString
@EqualsAndHashCode
public class TestMemoryQueue implements CommitterQueue {

    private final List<CommitterRequest> requests = new ArrayList<>();

    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private BatchConsumer batchConsumer;

    @Override
    public void init(
            CommitterContext committerContext,
            BatchConsumer batchConsumer) throws CommitterQueueException {
        this.batchConsumer = batchConsumer;
    }

    @Override
    public void queue(CommitterRequest request) throws CommitterQueueException {
        requests.add(request);
    }

    @Override
    public void clean() throws CommitterQueueException {
        requests.clear();
    }

    @Override
    public void close() throws CommitterQueueException {
        try {
            batchConsumer.consume(requests.iterator());
        } catch (CommitterException e) {
            throw new CommitterQueueException(e);
        }
    }

    public List<CommitterRequest> getAllRequests() {
        return requests;
    }
    public List<UpsertRequest> getUpsertRequests() {
        return requests.stream()
                .filter(UpsertRequest.class::isInstance)
                .map(UpsertRequest.class::cast)
                .toList();
    }
    public List<DeleteRequest> getDeleteRequests() {
        return requests.stream()
                .filter(DeleteRequest.class::isInstance)
                .map(DeleteRequest.class::cast)
                .toList();
    }
}
