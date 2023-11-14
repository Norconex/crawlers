/* Copyright 2010-2023 Norconex Inc.
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
package com.norconex.importer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.map.Properties;

class ImportHandlerTest {

    private Importer importer;
    private Properties metadata;

    @BeforeEach
    void setUp() throws Exception {
        importer = TestUtil.getTestConfigImporter();
        metadata = new Properties();
    }

    @AfterEach
    void tearDown() throws Exception {
        importer = null;
        metadata = null;
    }

    @Test
    void testHandlers() throws IOException {
        InputStream is = new BufferedInputStream(
                new FileInputStream(TestUtil.getAliceHtmlFile()));
        importer.importDocument(new ImporterRequest(is)
                .setMetadata(metadata)
                .setReference("alice.html"));
        is.close();

        // Test Constant
        Assertions.assertEquals("Lewis Carroll", metadata.getString("Author"));
    }
}
