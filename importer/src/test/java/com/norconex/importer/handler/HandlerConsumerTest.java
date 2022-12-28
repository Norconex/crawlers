/* Copyright 2021 Norconex Inc.
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
package com.norconex.importer.handler;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.norconex.commons.lang.ResourceLoader;
import com.norconex.commons.lang.io.CachedStreamFactory;
import com.norconex.commons.lang.map.Properties;
import com.norconex.commons.lang.xml.XML;
import com.norconex.importer.Importer;
import com.norconex.importer.ImporterConfig;
import com.norconex.importer.TestUtil;
import com.norconex.importer.doc.Doc;

class HandlerConsumerTest {

    @Test
    void testWriteRead() {
        ImporterConfig cfg = new ImporterConfig();
        cfg.loadFromXML(new XML(ResourceLoader.getXmlReader(getClass())));
        XML.assertWriteRead(cfg, "importer");
    }

    @Test
    void testXMLFlowConsumer() throws IOException {
        Properties metadata = new Properties();
        ImporterConfig cfg = new ImporterConfig();
        cfg.loadFromXML(new XML(ResourceLoader.getXmlReader(getClass())));
        Importer importer = new Importer(cfg);
        importer.importDocument(new Doc(
                "alice.html",
                new CachedStreamFactory().newInputStream(
                        FileUtils.openInputStream(TestUtil.getAliceHtmlFile())),
                metadata));
        assertEquals("Lewis Carroll", metadata.getString("Author"));
        assertEquals("HTML", metadata.getString("format"));
        assertEquals("refSuccess", metadata.getString("refTest"));
        assertEquals("scriptSuccess", metadata.getString("scriptTest"));
        assertEquals("numericSuccess", metadata.getString("numericTest"));
        assertEquals("domSuccess", metadata.getString("domTest"));
        assertEquals("dateSuccess", metadata.getString("dateTest"));
        assertEquals("blankTestSuccess", metadata.getString("blankTest"));
        assertEquals("notBlankTestSuccess", metadata.getString("notBlankTest"));
        Assertions.assertNull(metadata.getString("rejectTest"));
    }
}
