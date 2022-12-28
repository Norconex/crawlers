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

import static com.norconex.importer.parser.ParseState.PRE;
import static java.io.InputStream.nullInputStream;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.text.TextMatcher;
import com.norconex.importer.TestUtil;
import com.norconex.importer.handler.ImporterHandlerException;
import com.norconex.importer.handler.filter.impl.ReferenceFilter;

class FilterGroupResolverTest {

    @Test
    void testFilterGroupResolver() {
        var matchInclude = new ReferenceFilter(TextMatcher.basic("123.html"));
        matchInclude.setOnMatch(OnMatch.INCLUDE);
        var matchExclude = new ReferenceFilter(TextMatcher.basic("123.html"));
        matchExclude.setOnMatch(OnMatch.EXCLUDE);
        var noMatchInclude = new ReferenceFilter(TextMatcher.basic("456.html"));
        noMatchInclude.setOnMatch(OnMatch.INCLUDE);
        var noMatchExclude = new ReferenceFilter(TextMatcher.basic("456.html"));
        noMatchExclude.setOnMatch(OnMatch.EXCLUDE);

        // all INCLUDE matches
        assertThat(accept(matchInclude, matchInclude)).isTrue();
        // at least one INCLUDE matches
        assertThat(accept(matchInclude, noMatchInclude)).isTrue();
        // no INCLUDE matches
        assertThat(accept(noMatchInclude, noMatchInclude)).isFalse();

        // all EXCLUDE matches
        assertThat(accept(matchExclude, matchExclude)).isFalse();
        // at least one EXCLUDE matches
        assertThat(accept(matchExclude, noMatchExclude)).isFalse();
        // no EXCLUDE matches
        assertThat(accept(noMatchExclude, noMatchExclude)).isTrue();

        // exclude takes precedence
        assertThat(accept(matchInclude, matchExclude)).isFalse();
    }

    private boolean accept(ReferenceFilter... filters) {
        var doc = TestUtil.newHandlerDoc("123.html", nullInputStream());
        return FilterGroupResolver.<ReferenceFilter>builder()
            .filterResolver(f -> {
                try {
                    return f.acceptDocument(doc, nullInputStream(), PRE);
                } catch (ImporterHandlerException e) {
                    throw new RuntimeException(e);
                }
            })
            .build()
            .accepts(List.of(filters));
    }
}
