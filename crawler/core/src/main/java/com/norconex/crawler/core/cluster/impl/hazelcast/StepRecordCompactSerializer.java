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
package com.norconex.crawler.core.cluster.impl.hazelcast;

import com.hazelcast.nio.serialization.compact.CompactReader;
import com.hazelcast.nio.serialization.compact.CompactSerializer;
import com.hazelcast.nio.serialization.compact.CompactWriter;
import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.cluster.pipeline.StepRecord;

import lombok.NonNull;

/**
 * Hazelcast Compact serializer for {@link StepRecord}. Uses field-by-field
 * binary serialization for efficient cross-node transfer.
 */
public class StepRecordCompactSerializer
        implements CompactSerializer<StepRecord> {

    @Override
    public @NonNull StepRecord read(@NonNull CompactReader reader) {
        var record = new StepRecord();
        record.setPipelineId(reader.readString("pipelineId"));
        record.setStepId(reader.readString("stepId"));
        record.setUpdatedAt(reader.readInt64("updatedAt"));
        var statusName = reader.readString("status");
        if (statusName != null) {
            record.setStatus(PipelineStatus.valueOf(statusName));
        }
        record.setRunId(reader.readString("runId"));
        return record;
    }

    @Override
    public void write(
            @NonNull CompactWriter writer, @NonNull StepRecord record) {
        writer.writeString("pipelineId", record.getPipelineId());
        writer.writeString("stepId", record.getStepId());
        writer.writeInt64("updatedAt", record.getUpdatedAt());
        writer.writeString("status",
                record.getStatus() != null
                        ? record.getStatus().name()
                        : null);
        writer.writeString("runId", record.getRunId());
    }

    @Override
    public @NonNull String getTypeName() {
        return "StepRecord";
    }

    @Override
    public @NonNull Class<StepRecord> getCompactClass() {
        return StepRecord.class;
    }
}
