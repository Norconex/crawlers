/* Copyright 2023-2024 Norconex Inc.
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
package com.norconex.cfgconverter.yaml;

import java.io.Writer;

import com.norconex.cfgconverter.ConfigConverter;
import com.norconex.commons.lang.xml.Xml;

public class XmlV4ToYamlV4ConfigConverter implements ConfigConverter {

    @Override
    public void convert(Xml input, Writer output) {
        //TODO May actually not be need it at all since Jackson lib does it well 
    }

}
