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

import java.util.function.Predicate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.norconex.commons.lang.Operator;

import lombok.Data;

@Data
public class NumericValueMatcher //NOSONAR we want to support null
        implements Predicate<Double> {
    private final Operator operator;
    private final double number;

    @JsonCreator
    public NumericValueMatcher(
            @JsonProperty("operator") Operator operator,
            @JsonProperty("number") double number) {
        this.operator = operator;
        this.number = number;
    }

    @Override
    public boolean test(Double number) {
        if (number == null) {
            return false;
        }
        var op = defaultIfNull(operator, EQUALS);
        return op.evaluate(number, this.number);
    }
}