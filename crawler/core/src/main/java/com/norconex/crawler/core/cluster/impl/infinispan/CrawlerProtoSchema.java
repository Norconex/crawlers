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

import org.infinispan.protostream.GeneratedSchema;
import org.infinispan.protostream.annotations.ProtoSchema;
import org.infinispan.protostream.annotations.ProtoSyntax;

import com.norconex.crawler.core.cluster.pipeline.PipelineStatus;
import com.norconex.crawler.core.ledger.ProcessingStatus;

@ProtoSchema(
    syntax = ProtoSyntax.PROTO3,
    includeClasses = {
            CrawlEntryProtoAdapter.class,
            StringSerializedObject.class,
            StepRecord.class,
            PipelineStatus.class,
            ProcessingStatus.class
    },
    //    schemaPackageName = "ledger",
    //    ,
    //    schemaPackageName = "ledger"
    //,
    schemaFileName = "crawler.proto",
    schemaFilePath = "proto/"
)
public interface CrawlerProtoSchema extends GeneratedSchema {
    // No methods needed here
}
