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
package com.norconex.importer.handler.condition.impl;

import static com.norconex.commons.lang.Operator.EQUALS;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.startsWithIgnoreCase;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.Operator;

import lombok.Data;
import lombok.NonNull;

/**
 * Immutable date-value matcher, supporting both date expressions and
 * standard operators.
 */
@Data
public class DateValueMatcher implements Predicate<ZonedDateTime> {

    private final Operator operator;
    @JsonIgnore
    private final DateProvider dateProvider;

    public DateValueMatcher(
            Operator operator,
            @NonNull DateProvider dateProvider) {
        this.operator = operator;
        this.dateProvider = dateProvider;
    }

    @JsonCreator
    public DateValueMatcher(
            @JsonProperty("operator") Operator operator,
            @JsonProperty("date") String dateTimeExpression,
            @JsonProperty("zoneId") ZoneId zoneId) {
        this.operator = operator;
        dateProvider =
                DateProviderFactory.create(dateTimeExpression, zoneId);
    }

    @Override
    public boolean test(ZonedDateTime zdt) {
        if (zdt == null) {
            return false;
        }

        // if the date obtained by the supplier (the date value or logic
        // configured) starts with TODAY, we truncate that date to
        // ensure we are comparing apples to apples. Else, one must ensure
        // the date format matches for proper comparisons.
        var resolvedZdt = zdt;
        if (startsWithIgnoreCase(dateProvider.toString(), "today")) {
            resolvedZdt = resolvedZdt.truncatedTo(ChronoUnit.DAYS);
        }
        var op = defaultIfNull(operator, EQUALS);
        return op.evaluate(
                resolvedZdt.toInstant(),
                dateProvider.getDateTime().toInstant());
    }

    @JsonProperty("date")
    String dateProviderAsString() {
        return dateProvider.toString();
    }

    @JsonProperty("zoneId")
    String zoneIdAsString() {
        return Optional.ofNullable(dateProvider.getZoneId())
                .map(ZoneId::toString)
                .orElse(null);
    }
}