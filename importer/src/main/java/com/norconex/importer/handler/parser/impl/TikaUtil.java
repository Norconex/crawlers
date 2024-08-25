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
package com.norconex.importer.handler.parser.impl;

import java.util.List;

import org.apache.tika.metadata.HttpHeaders;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;

import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.map.Properties;

class TikaUtil {

    private TikaUtil() {
    }

    static void metadataToProperties(
            Metadata tikaMeta, Properties metadata
    ) {
        var names = tikaMeta.names();
        for (String name : names) {
            if (TikaCoreProperties.RESOURCE_NAME_KEY.equals(name)) {
                continue;
            }
            var nxValues = metadata.getStrings(name);
            var tikaValues = tikaMeta.getValues(name);
            for (String tikaValue : tikaValues) {
                if (!containsSameValue(name, nxValues, tikaValue)) {
                    metadata.add(name, tikaValue);
                } else {
                    metadata.set(name, tikaValue);
                }
            }
        }
    }

    private static boolean containsSameValue(
            String name, List<String> nxValues, String tikaValue
    ) {
        if (EqualsUtil.equalsAnyIgnoreCase(
                name,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.CONTENT_ENCODING
        )) {
            var tk = tikaValue.replaceAll("[\\s]", "");
            for (String nxValue : nxValues) {
                if (nxValue.replaceAll("[\\s]", "").equalsIgnoreCase(tk)) {
                    return true;
                }
            }
            return false;
        }
        return nxValues.contains(tikaValue);
    }
}
