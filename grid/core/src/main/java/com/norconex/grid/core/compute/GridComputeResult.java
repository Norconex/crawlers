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

import java.util.ArrayList;
import java.util.List;

import com.norconex.commons.lang.ExceptionUtil;
import com.norconex.commons.lang.ExceptionUtil.ExceptionMessage;
import com.norconex.commons.lang.collection.CollectionUtil;
import com.norconex.grid.core.GridResult;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * The return value of a grid job. Even if the job ran on a single node,
 * all nodes will be getting the output.
 * @param <T> result type
 */
@Data
@Accessors(chain = true)
public class GridComputeResult<T>
        implements GridResult<T, GridComputeState> {

    private T value;
    private GridComputeState state;
    private final List<ExceptionMessage> exceptionStack = new ArrayList<>();

    public GridComputeResult<T>
            setExceptionStack(List<ExceptionMessage> exceptionStack) {
        CollectionUtil.setAll(this.exceptionStack, exceptionStack);
        return this;
    }

    public boolean containsException(Throwable t) {
        if (t == null) {
            return false;
        }
        return exceptionStack.stream()
                .map(ExceptionMessage::getExceptionType)
                .anyMatch(cls -> t.getClass().isAssignableFrom(cls));
    }

    public static <T> GridComputeResult<T> of(
            T resultValue, GridComputeState state, Throwable exception) {
        return new GridComputeResult<T>()
                .setValue(resultValue)
                .setState(state)
                .setExceptionStack(
                        ExceptionUtil.getExceptionMessageList(exception));
    }
}
