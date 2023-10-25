/* Copyright 2016-2023 Norconex Inc.
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
package com.norconex.importer.parser;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.norconex.commons.lang.collection.CollectionUtil;

import lombok.Data;
import lombok.NonNull;

/**
 * Configuration settings influencing how documents are parsed by various
 * parsers.  These settings are not applicable to all parsers and some parsers
 * may decide not to support some of these settings (for not being able to
 * or else).
 */
@Data
public class ParseOptions { //{

    @NonNull
    private OCRConfig ocrConfig = new OCRConfig();
    @NonNull
    private EmbeddedConfig embeddedConfig = new EmbeddedConfig();
    private final Map<String, String> options = new HashMap<>();

    public void setOptions(Map<String, String> options) {
        CollectionUtil.setAll(this.options, options);
    }
    public Map<String, String> getOptions() {
        return Collections.unmodifiableMap(options);
    }

//    @Override
//    public void loadFromXML(XML xml) {
//        ocrConfig.loadFromXML(xml.getXML("ocr"));
//        embeddedConfig.loadFromXML(xml.getXML("embedded"));
//        setOptions(
//                xml.getStringMap("options/option", attr("name"), ".", options));
//    }
//    @Override
//    public void saveToXML(XML xml) {
//        xml.addElement("ocr", ocrConfig);
//        xml.addElement("embedded", embeddedConfig);
//        xml.addElementMap("options", "option", "name", options);
//    }
}
