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
package com.norconex.crawler.web.link;

import static com.norconex.commons.lang.text.TextMatcher.basic;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.xml.XML;
import com.norconex.crawler.core.doc.CrawlDoc;

class AbstractLinkExtractorTest {

    @Test
    void test() {
        var extractor = new AbstractLinkExtractor() {
            @Override
            protected void saveLinkExtractorToXML(XML xml) {
                // NOOP
            }
            @Override
            protected void loadLinkExtractorFromXML(XML xml) {
                // NOOP
            }
            @Override
            public void extractLinks(Set<Link> links, CrawlDoc doc)
                    throws IOException {
                // NOOP
            }
        };
        extractor.addRestriction(
                new PropertyMatcher(basic("faaa"), basic("vaaa")));
        extractor.addRestrictions(List.of(
            new PropertyMatcher(basic("fbbb"), basic("vbbb")),
            new PropertyMatcher(basic("fccc"), basic("vccc"))
        ));
        assertThat((List<?>) extractor.getRestrictions()).hasSize(3);

        extractor.removeRestriction("fbbb");
        assertThat((List<?>) extractor.getRestrictions()).hasSize(2);

        extractor.removeRestriction(
                new PropertyMatcher(basic("faaa"), basic("vaaa")));
        assertThat((List<?>) extractor.getRestrictions()).hasSize(1);

        extractor.setRestrictions(List.of(
            new PropertyMatcher(basic("fddd"), basic("vddd")),
            new PropertyMatcher(basic("feee"), basic("veee"))
        ));
        assertThat((List<?>) extractor.getRestrictions()).hasSize(2);

        extractor.clearRestrictions();
        assertThat((List<?>) extractor.getRestrictions()).isEmpty();
    }
}
