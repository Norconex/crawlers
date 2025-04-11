/* Copyright 2010-2025 Norconex Inc.
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
package com.norconex.crawler.web.doc.operations.delay.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * <p>
 * Configuration for {@link GenericDelayResolver}.
 * </p>
 */
@Data
@Accessors(chain = true)
public class GenericDelayResolverConfig extends BaseDelayResolverConfig {

    private final List<DelaySchedule> schedules = new ArrayList<>();

    public List<DelaySchedule> getSchedules() {
        return Collections.unmodifiableList(schedules);
    }

    public GenericDelayResolverConfig setSchedules(
            List<DelaySchedule> schedules) {
        CollectionUtil.setAll(this.schedules, schedules);
        return this;
    }
}
