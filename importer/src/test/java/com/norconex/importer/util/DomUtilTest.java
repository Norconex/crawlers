/* Copyright 2024 Norconex Inc.
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
package com.norconex.importer.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.jsoup.Jsoup;
import org.jsoup.parser.HtmlTreeBuilder;
import org.jsoup.parser.XmlTreeBuilder;
import org.junit.jupiter.api.Test;

class DomUtilTest {

    @Test
    void testToJSoupParser() {
        // HTML
        var parser = DomUtil.toJSoupParser("html");
        assertThat(parser.getTreeBuilder()).isInstanceOf(HtmlTreeBuilder.class);
        // DOM
        parser = DomUtil.toJSoupParser("xml");
        assertThat(parser.getTreeBuilder()).isInstanceOf(XmlTreeBuilder.class);
    }

    @Test
    void testGetElementValue() {
        var doc = Jsoup.parse("""
                <html><body><div class="AAA">blah</div></body></html>
                """);

        assertThat(DomUtil.getElementValue(doc, "attr()")).isNull();
        assertThat(DomUtil.getElementValue(doc, "BAD")).isEqualTo("blah");

    }
}
