/* Copyright 2021-2023 Norconex Inc.
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

import java.util.function.Predicate;

import com.norconex.importer.handler.DocContext;

/**
 * A condition usually used in flow creation when configuring
 * importer handlers.
 */
@FunctionalInterface
public interface Condition extends Predicate<DocContext> {

    //TODO needed?
    //TODO extend Predicate and replace method or have a default one?
    // or eliminate in favor of predicate?

}
