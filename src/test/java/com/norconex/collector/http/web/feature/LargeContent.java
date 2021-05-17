/* Copyright 2019-2021 Norconex Inc.
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
package com.norconex.collector.http.web.feature;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.Assertions;

import com.norconex.collector.http.web.AbstractTestFeature;
import com.norconex.committer.core3.impl.MemoryCommitter;

/**
 * Test that large files are processed properly (&gt; 2MB).
 * @author Pascal Essiembre
 */
public class LargeContent extends AbstractTestFeature {

    private static final int MIN_SIZE = 5 * 1024 *1024;

    @Override
    protected void doHtmlService(PrintWriter out) throws Exception {
        // Return more than 2 MB of text.
        out.write(RandomStringUtils.randomAlphanumeric(MIN_SIZE));
    }

    @Override
    protected void doTestMemoryCommitter(MemoryCommitter committer)
            throws Exception {
        String txt = IOUtils.toString(
                committer.getUpsertRequests().get(0).getContent(),
                StandardCharsets.UTF_8);
        Assertions.assertTrue(txt.length() >= MIN_SIZE);
    }
}
