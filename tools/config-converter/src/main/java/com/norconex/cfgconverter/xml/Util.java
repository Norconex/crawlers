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
package com.norconex.cfgconverter.xml;

import java.util.Optional;
import java.util.function.UnaryOperator;

import org.apache.commons.lang3.StringUtils;

import com.norconex.commons.lang.xml.Xml;

import lombok.NonNull;

final class Util {
    private Util() {}

    public static void setClass(
            @NonNull Xml xml,
            @NonNull UnaryOperator<String> replacer) {
        setAttr(xml, "class", replacer);
    }

    public static void setAttr(
            @NonNull Xml xml,
            @NonNull String attr,
            @NonNull UnaryOperator<String> replacer) {
        Optional.ofNullable(xml.getString("@" + attr)).ifPresent(val -> {
            xml.setAttribute(attr, replacer.apply(val));
        });
    }

    public static void setElemValue(
            @NonNull Xml xml,
            @NonNull String elemName,
            @NonNull UnaryOperator<String> replacer) {
        xml.ifXML(elemName,
                x -> x.setTextContent(replacer.apply(
                        StringUtils.trimToEmpty(x.getString(".")))));
    }
}
