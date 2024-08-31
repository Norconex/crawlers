/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.committer.core.service;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import com.norconex.committer.core.Committer;
import com.norconex.commons.lang.event.Event;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Default committer events.
 */
@Data
@Setter(value = AccessLevel.NONE)
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class CommitterServiceEvent extends Event {

    private static final long serialVersionUID = 1L;

    public static final String COMMITTER_SERVICE_INIT_BEGIN =
            "COMMITTER_SERVICE_INIT_BEGIN";
    public static final String COMMITTER_SERVICE_INIT_END =
            "COMMITTER_SERVICE_INIT_END";
    public static final String COMMITTER_SERVICE_UPSERT_BEGIN =
            "COMMITTER_SERVICE_UPSERT_BEGIN";
    public static final String COMMITTER_SERVICE_UPSERT_END =
            "COMMITTER_SERVICE_UPSERT_END";
    public static final String COMMITTER_SERVICE_DELETE_BEGIN =
            "COMMITTER_SERVICE_DELETE_BEGIN";
    public static final String COMMITTER_SERVICE_DELETE_END =
            "COMMITTER_SERVICE_DELETE_END";
    public static final String COMMITTER_SERVICE_CLEAN_BEGIN =
            "COMMITTER_SERVICE_CLEAN_BEGIN";
    public static final String COMMITTER_SERVICE_CLEAN_END =
            "COMMITTER_SERVICE_CLEAN_END";
    public static final String COMMITTER_SERVICE_CLOSE_BEGIN =
            "COMMITTER_SERVICE_CLOSE_BEGIN";
    public static final String COMMITTER_SERVICE_CLOSE_END =
            "COMMITTER_SERVICE_CLOSE_END";

    private final transient Object subject;
    private final transient List<Committer> committers;

    @Override
    public String toString() {
        // Cannot use ReflectionToStringBuilder here to prevent
        // "An illegal reflective access operation has occurred"
        return new ToStringBuilder(this, ToStringStyle.NO_CLASS_NAME_STYLE)
                //                .appendSuper(super.toString())
                .append(
                        "committers", '[' + committers.stream()
                                .map(c -> c.getClass().getSimpleName())
                                .collect(Collectors.joining(",")) + ']')
                .append("subject", subject)
                .build();
    }
}
