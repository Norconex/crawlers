/* Copyright 2022-2024 Norconex Inc.
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

import java.util.Iterator;

import com.norconex.committer.core.batch.AbstractBatchCommitter;
import com.norconex.committer.core.batch.BaseBatchCommitterConfig;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@ToString
public class TestBatchCommitter
        extends AbstractBatchCommitter<BaseBatchCommitterConfig> {

    @Getter
    private final BaseBatchCommitterConfig configuration =
            new BaseBatchCommitterConfig();

    @Override
    protected void commitBatch(Iterator<CommitterRequest> it)
            throws CommitterException {
    }
}
