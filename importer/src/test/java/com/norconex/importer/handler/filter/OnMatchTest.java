/* Copyright 2022 Norconex Inc.
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
package com.norconex.importer.handler.filter;

import static com.norconex.importer.handler.filter.OnMatch.EXCLUDE;
import static com.norconex.importer.handler.filter.OnMatch.INCLUDE;
import static com.norconex.importer.handler.filter.OnMatch.loadFromXML;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.xml.XML;

class OnMatchTest {

    @Test
    void testIncludeIfNull() {
        assertThat(OnMatch.includeIfNull(null)).isSameAs(INCLUDE);
    }

    @Test
    void testExcludeIfNull() {
        assertThat(OnMatch.excludeIfNull(null)).isSameAs(EXCLUDE);
    }

    @Test
    void testLoadFromXML() {
        assertThat(loadFromXML(null, null)).isNull();
        assertThat(loadFromXML(null, EXCLUDE)).isSameAs(EXCLUDE);

        assertThat(loadFromXML(new XML("<test onMatch=\"exclude\"/>"), null))
                .isSameAs(EXCLUDE);
        assertThat(loadFromXML(new XML("<test onMatch=\"include\"/>"), null))
                .isSameAs(INCLUDE);
        assertThat(loadFromXML(new XML("<test />"), null)).isNull();
    }

    @Test
    void testSaveToXML() {
        var xml = new XML("test");

        OnMatch.saveToXML(xml, INCLUDE);
        assertThat(xml).hasToString("<test onMatch=\"INCLUDE\"/>");

        xml.clear();
        OnMatch.saveToXML(xml, EXCLUDE);
        assertThat(xml).hasToString("<test onMatch=\"EXCLUDE\"/>");

        xml.clear();
        OnMatch.saveToXML(xml, null);
        assertThat(xml).hasToString("<test/>");
    }
}
