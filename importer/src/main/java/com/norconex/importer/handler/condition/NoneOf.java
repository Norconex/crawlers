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
package com.norconex.importer.handler.condition;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.norconex.importer.handler.DocHandlerContext;

import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
@JsonTypeName(NoneOf.NAME)
public class NoneOf extends ConditionGroup {

    /** Serialization ID name. */
    public static final String NAME = "noneOf";

    @JsonCreator
    public NoneOf(@NonNull List<Condition> conditions) {
        super(conditions);
    }

    @Override
    public boolean test(DocHandlerContext ctx) {
        var matching = conditions.stream()
                .filter(cond -> execCondition(cond, ctx))
                .findFirst();
        matching.ifPresentOrElse(
                c -> LOG.debug("At least 1 of 'none of all' {} conditions "
                        + "was matched. First match: {}",
                        conditions.size(), c),
                () -> LOG.debug("None of {} conditions matched.",
                        conditions.size()));
        return matching.isEmpty();
    }
}
