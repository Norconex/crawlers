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
package com.norconex.crawler.core.junit.cluster.node;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.bean.BeanUtil;

import lombok.Data;
import lombok.experimental.Accessors;
import lombok.experimental.FieldNameConstants;

@Data
@Accessors(chain = true)
@FieldNameConstants(asEnum = true)
public class CaptureFlags {
    static final String SYS_PROP_CAPTURES = "node.captures";

    private boolean events;
    private boolean caches;
    private boolean stdout;
    private boolean stderr;

    String asJvmSysProp() {
        List<String> captures = new ArrayList<>();
        for (Fields field : CaptureFlags.Fields.values()) {
            if ((boolean) BeanUtil.getValue(this, field.name())) {
                captures.add(field.name());
            }
        }
        if (captures.isEmpty()) {
            return "";
        }
        return "-D" + SYS_PROP_CAPTURES + "=" + String.join(",", captures);

    }

    static CaptureFlags fromSysProp() {
        var str =
                StringUtils.trimToEmpty(System.getProperty(SYS_PROP_CAPTURES));
        var fields = StringUtils.split(str, ',');
        var captures = new CaptureFlags();
        for (String field : fields) {
            BeanUtil.setValue(captures, field, true);
        }
        return captures;
    }
}
