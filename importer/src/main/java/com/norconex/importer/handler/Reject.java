/* Copyright 2023 Norconex Inc.
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
package com.norconex.importer.handler;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.config.Configurable;
import com.norconex.importer.handler.condition.Condition;

import lombok.Data;

/**
 * Special handler that marks a document as being "rejected", with an
 * optional custom message for event logging. If wrapped in a
 * {@link Condition}, information about the condition will also be logged.
 */

//TODO make it built-into Flow as a way to opt-out of the flow at any time
// MAYBE as an optional value in a then/else block?

@Data
public class Reject implements DocumentHandler, Configurable<RejectConfig> {

    private final RejectConfig configuration = new RejectConfig();

    @Override
    public void accept(HandlerContext ctx) {
        Object by = null;
        if (StringUtils.isNotBlank(configuration.getMessage())) {
            by = configuration.getMessage();
        }
        if (by == null && ctx.condition() != null) {
            by = ctx.condition();
        }
        if (by == null) {
            by = this;
        }
        ctx.rejectedBy(by);
    }
}
