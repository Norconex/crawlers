/* Copyright 2020-2022 Norconex Inc.
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

import org.apache.commons.lang3.builder.ToStringExclude;

import com.norconex.commons.lang.map.Properties;

import lombok.Data;
import lombok.NonNull;

/**
 * A committer deletion request. Metadata associated with a deletion
 * request is typically minimal in comparison with an addition or update.
 * It is even possible for it to be empty.
 */
@Data
public class DeleteRequest implements CommitterRequest {

    private final String reference;
    @ToStringExclude
    private final Properties metadata = new Properties();

    public DeleteRequest(@NonNull String reference, Properties metadata) {
        this.reference = reference;
        this.metadata.putAll(metadata);
    }
}
