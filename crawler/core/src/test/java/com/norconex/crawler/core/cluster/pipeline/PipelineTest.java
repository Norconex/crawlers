/* Copyright 2025-2026 Norconex Inc.
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
package com.norconex.crawler.core.cluster.pipeline;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.apache.commons.collections4.Bag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.norconex.crawler.core.junit.WithTestWatcherLogging;
import com.norconex.crawler.core.session.CrawlSession;

@WithTestWatcherLogging
@Timeout(30)
class PipelineTest {

    private static Step step(String id) {
        return new Step() {
            @Override
            public String getId() {
                return id;
            }

            @Override
            public boolean isDistributed() {
                return false;
            }

            @Override
            public void execute(CrawlSession s) {
            }

            @Override
            public void stop(CrawlSession s) {
            }

            @Override
            public PipelineStatus reduce(
                    CrawlSession s, Bag<PipelineStatus> b) {
                return PipelineStatus.COMPLETED;
            }
        };
    }

    // -----------------------------------------------------------------
    // getFirstStep / getLastStep on empty pipeline
    // -----------------------------------------------------------------

    @Test
    void getFirstStep_emptyPipeline_returnsNull() {
        var pipeline = new Pipeline("p1");
        assertThat(pipeline.getFirstStep()).isNull();
    }

    @Test
    void getLastStep_emptyPipeline_returnsNull() {
        var pipeline = new Pipeline("p1");
        assertThat(pipeline.getLastStep()).isNull();
    }

    // -----------------------------------------------------------------
    // addStep / getFirstStep / getLastStep / order preserved
    // -----------------------------------------------------------------

    @Test
    void addStep_singleStep_firstAndLastAreSame() {
        var pipeline = new Pipeline("p1");
        var s = step("only");
        pipeline.addStep(s);

        assertThat(pipeline.getFirstStep()).isSameAs(s);
        assertThat(pipeline.getLastStep()).isSameAs(s);
    }

    @Test
    void addStep_multipleSteps_orderPreserved() {
        var pipeline = new Pipeline("p1");
        var s1 = step("first");
        var s2 = step("middle");
        var s3 = step("last");
        pipeline.addStep(s1);
        pipeline.addStep(s2);
        pipeline.addStep(s3);

        assertThat(pipeline.getFirstStep()).isSameAs(s1);
        assertThat(pipeline.getLastStep()).isSameAs(s3);
    }

    // -----------------------------------------------------------------
    // Constructor with List<Step>
    // -----------------------------------------------------------------

    @Test
    void constructorWithList_populatesStepsInOrder() {
        var s1 = step("a");
        var s2 = step("b");
        var pipeline = new Pipeline("p2", List.of(s1, s2));

        assertThat(pipeline.getFirstStep()).isSameAs(s1);
        assertThat(pipeline.getLastStep()).isSameAs(s2);
    }

    // -----------------------------------------------------------------
    // getStep
    // -----------------------------------------------------------------

    @Test
    void getStep_existingId_returnsStep() {
        var s = step("myStep");
        var pipeline = new Pipeline("p1", List.of(s));

        assertThat(pipeline.getStep("myStep")).isSameAs(s);
    }

    @Test
    void getStep_unknownId_throwsNPE() {
        var pipeline = new Pipeline("p1", List.of(step("a")));

        assertThatThrownBy(() -> pipeline.getStep("no-such-step"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("no-such-step");
    }

    // -----------------------------------------------------------------
    // id / steps size
    // -----------------------------------------------------------------

    @Test
    void getId_returnsConstructorValue() {
        var pipeline = new Pipeline("my-pipeline");
        assertThat(pipeline.getId()).isEqualTo("my-pipeline");
    }

    @Test
    void steps_countMatchesAdded() {
        var pipeline = new Pipeline("p1");
        pipeline.addStep(step("x"));
        pipeline.addStep(step("y"));
        assertThat(pipeline.getSteps()).hasSize(2);
    }
}
