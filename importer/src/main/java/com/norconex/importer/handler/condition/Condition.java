/* Copyright 2021-2025 Norconex Inc.
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.function.FailablePredicate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.importer.ImporterRuntimeException;
import com.norconex.importer.handler.DocHandlerContext;

import lombok.Data;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

/**
 * A condition usually used in flow creation when configuring
 * importer handlers.
 */
@FunctionalInterface
public interface Condition
        extends FailablePredicate<DocHandlerContext, IOException> {

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.WRAPPER_OBJECT
    )
    @Data
    abstract static class ConditionGroup implements Condition {
        final List<Condition> conditions = new ArrayList<>();

        ConditionGroup(@NonNull List<Condition> conditions) {
            CollectionUtil.setAll(this.conditions, conditions);
            CollectionUtil.removeNulls(this.conditions);
        }

        public List<Condition> getConditions() {
            return Collections.unmodifiableList(conditions);
        }

        boolean execCondition(Condition cond, DocHandlerContext ctx) {
            try {
                return cond.test(ctx);
            } catch (IOException e) {
                throw new ImporterRuntimeException(
                        "Could not execute condition: "
                                + cond.getClass().getName(),
                        e);
            }
        }
    }

    @Data
    @Slf4j
    @JsonTypeName("anyOf")
    public static class AnyOf extends ConditionGroup {
        @JsonCreator
        public AnyOf(@NonNull List<Condition> conditions) {
            super(conditions);
        }

        @Override
        public boolean test(DocHandlerContext ctx) {
            var matching = conditions.stream()
                    .filter(cond -> execCondition(cond, ctx))
                    .findFirst();
            matching.ifPresentOrElse(
                    c -> LOG.debug("At least 1 of any {} conditions matched. "
                            + "First match: {}", conditions.size(), c),
                    () -> LOG.debug("None of any {} conditions matched.",
                            conditions.size()));
            return matching.isPresent();
        }
    }

    @Data
    @Slf4j
    @JsonTypeName("allOf")
    public static class AllOf extends ConditionGroup {
        @JsonCreator
        public AllOf(@NonNull List<Condition> conditions) {
            super(conditions);
        }

        @Override
        public boolean test(DocHandlerContext ctx) {
            var matching = conditions.stream()
                    .filter(cond -> !execCondition(cond, ctx))
                    .findFirst();
            matching.ifPresentOrElse(
                    c -> LOG.debug("At least 1 of all {} conditions was not "
                            + "matched. First mismatch: {}", conditions.size(),
                            c),
                    () -> LOG.debug("All of {} conditions matched.",
                            conditions.size()));
            return matching.isEmpty();
        }
    }

    @Data
    @Slf4j
    @JsonTypeName("noneOf")
    public static class NoneOf extends ConditionGroup {
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

}
