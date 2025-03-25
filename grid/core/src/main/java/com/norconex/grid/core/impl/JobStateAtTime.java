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
package com.norconex.grid.core.impl;

import java.io.Serializable;
import java.time.Duration;

import com.norconex.grid.core.compute.JobState;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JobStateAtTime implements Serializable {
    private static final long serialVersionUID = 1L;
    private JobState state;
    private long time;
    //TODO remove node name from grid interface? Or make it
    // localAddress.toString() for those who need a name but by default
    // we don't use it
    private String nodeName;

    public Duration since() {
        return Duration.ofMillis(time);
    }
}
