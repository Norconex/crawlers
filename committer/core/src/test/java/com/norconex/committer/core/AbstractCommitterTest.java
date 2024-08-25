/* Copyright 2022-2023 Norconex Inc.
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
package com.norconex.committer.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.norconex.committer.core.impl.MemoryCommitter;
import com.norconex.commons.lang.map.MapUtil;
import com.norconex.commons.lang.map.PropertyMatcher;
import com.norconex.commons.lang.text.TextMatcher;

class AbstractCommitterTest {

    @Test
    void testRestrictions() throws CommitterException {
        try (var c = new MemoryCommitter()) {
            var cfg = c.getConfiguration()
                    .addRestriction(
                            new PropertyMatcher(TextMatcher.basic("blah1"))
                    )
                    .addRestrictions(
                            Arrays.asList(
                                    new PropertyMatcher(
                                            TextMatcher.basic("blah2")
                                    ),
                                    new PropertyMatcher(
                                            TextMatcher.basic("blah3")
                                    ),
                                    new PropertyMatcher(
                                            TextMatcher.basic("yo1")
                                    ),
                                    new PropertyMatcher(
                                            TextMatcher.basic("yo2")
                                    )
                            )
                    );

            assertThat((List<?>) cfg.getRestrictions()).hasSize(5);

            cfg.removeRestriction("blah2");
            assertThat((List<?>) cfg.getRestrictions()).hasSize(4);

            cfg.removeRestriction(
                    new PropertyMatcher(TextMatcher.basic("blah3"))
            );
            assertThat((List<?>) cfg.getRestrictions()).hasSize(3);

            cfg.clearRestrictions();
            assertThat((List<?>) cfg.getRestrictions()).isEmpty();

            // test accept
            cfg.addRestriction(
                    new PropertyMatcher(
                            TextMatcher.basic("field"), TextMatcher.basic("yes")
                    )
            );
            c.init(CommitterContext.builder().build());

            assertThat(
                    c.accept(
                            TestUtil.upsertRequest(
                                    "ref", "content", "field", "yes"
                            )
                    )
            ).isTrue();
            assertThat(
                    c.accept(
                            TestUtil.upsertRequest(
                                    "ref", "content", "field", "no"
                            )
                    )
            ).isFalse();
        }
    }

    @Test
    void testFieldMappings() throws CommitterException {
        try (var c = new MemoryCommitter()) {
            var cfg = c.getConfiguration()
                    .setFieldMapping("fromField1", "toField1")
                    .setFieldMappings(
                            MapUtil.toMap(
                                    "fromField2", "toField2",
                                    "fromField3", "toField3",
                                    "fromField4", "toField4"
                            )
                    );
            assertThat(cfg.getFieldMappings()).hasSize(4);

            cfg.removeFieldMapping("fromField2");
            assertThat(cfg.getFieldMappings()).hasSize(3);

            cfg.clearFieldMappings();
            assertThat(cfg.getFieldMappings()).isEmpty();

            // test apply field mappings
            cfg.setFieldMapping("fromFieldA", "toFieldA");
            cfg.setFieldMapping("fromFieldB", "toFieldB");
            c.init(CommitterContext.builder().build());

            c.upsert(
                    TestUtil.upsertRequest(
                            "refA", "content", "fromFieldA", "valueA"
                    )
            );
            c.delete(
                    TestUtil.deleteRequest(
                            "refB", "fromFieldB", "valueB"
                    )
            );

            assertThat(
                    c.getUpsertRequests().get(0).getMetadata().getString(
                            "toFieldA"
                    )
            ).isEqualTo("valueA");
            assertThat(
                    c.getDeleteRequests().get(0).getMetadata().getString(
                            "toFieldB"
                    )
            ).isEqualTo("valueB");
        }
    }
}
