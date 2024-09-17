/* Copyright 2022-2024 Norconex Inc.
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
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import org.apache.commons.io.input.BrokenInputStream;
import org.junit.jupiter.api.Test;

class CommitterUtilTest {

    @Test
    void testGetContentAsString() throws CommitterException {
        assertThat(
                CommitterUtil.getContentAsString(
                        TestUtil.upsertRequest("ref", "gotit")))
                                .isEqualTo("gotit");
        assertThat(
                CommitterUtil.getContentAsString(
                        TestUtil.deleteRequest("ref"))).isNull();
        assertThatExceptionOfType(CommitterException.class).isThrownBy(() -> {
            CommitterUtil.getContentAsString(
                    new UpsertRequest(
                            "ref", null, new BrokenInputStream()));
        });
    }

    @Test
    void testExtractSourceIdValueCommitterRequestString() {
        var req = TestUtil.upsertRequest(
                "someRef", "content", "srcField", "srcValue");

        // get source id from request ref
        assertThat(CommitterUtil.extractSourceIdValue(req, null))
                .isEqualTo("someRef");

        // get source id from request metadata field, keeping it
        assertThat(CommitterUtil.extractSourceIdValue(req, "srcField", true))
                .isEqualTo("srcValue");
        assertThat(req.getMetadata().getString("srcField"))
                .isEqualTo("srcValue");

        // get source id from request metadata field, not keeping it
        assertThat(CommitterUtil.extractSourceIdValue(req, "srcField"))
                .isEqualTo("srcValue");
        assertThat(req.getMetadata().getString("srcField"))
                .isNull();

        // at this point, source field does not exist, default to ref
        assertThat(CommitterUtil.extractSourceIdValue(req, "srcField"))
                .isEqualTo("someRef");
    }

    @Test
    void testApplyTargetContent() throws CommitterException {
        var req = TestUtil.upsertRequest("someRef", "content");

        // test storing the content stream into a field
        CommitterUtil.applyTargetContent(req, "overHere");
        assertThat(req.getMetadata().getString("overHere"))
                .isEqualTo("content");
    }

    @Test
    void testApplyTargetId() {
        var req = TestUtil.upsertRequest(
                "someRef", "content",
                "fromId", "myValue");

        // test storing the content stream into a field
        CommitterUtil.applyTargetId(req, "fromId", "toId");
        assertThat(req.getMetadata().getString("toId")).isEqualTo("myValue");
    }
}
