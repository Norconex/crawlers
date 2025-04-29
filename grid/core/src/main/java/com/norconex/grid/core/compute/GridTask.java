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
package com.norconex.grid.core.compute;

import java.io.Serializable;

import com.norconex.grid.core.GridContext;

/**
 * Core interface for tasks that can be executed on the grid
 */
public interface GridTask extends Serializable {

    //MAYBE: add option to wait for all tasks to be done or not??

    String getId();

    ExecutionMode getExecutionMode();

    //TODO support the return value or return void. A few options:
    // 1. Return an object that could either be single value or a List
    // 2. Always return a List
    // 3. Add with the task a new property GridResultAgreggator/Reducer
    Serializable execute(GridContext gridContext);

}
