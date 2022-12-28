/* Copyright 2010-2022 Norconex Inc.
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
package com.norconex.importer.parser.impl;

import java.io.IOException;

import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.parser.AutoDetectParser;
import org.xml.sax.SAXException;

import com.norconex.importer.ImporterRuntimeException;


/**
 * Parser using auto-detection of document content-type to figure out
 * which specific parser to invoke to best parse a document.
 */
public class FallbackParser extends AbstractTikaParser {

    /**
     * Creates a new parser.
     */
    public FallbackParser() {
        super(new AutoDetectParser(tikaConfig()));
    }

    private static TikaConfig tikaConfig() {
        try {
            return new TikaConfig(
                    FallbackParser.class.getResource("/tika-config.xml"));
        } catch (TikaException | IOException | SAXException e) {
            throw new ImporterRuntimeException(
                    "Could not load tika configuration file.", e);
        }
    }
}
