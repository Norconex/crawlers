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
package com.norconex.importer.handler.parser.impl;

import java.nio.file.Path;

import lombok.Data;
import lombok.NonNull;
import lombok.experimental.Accessors;


/**
 * Parser class when no other handlers are specified.
 * The importer uses Apache Tika parser in its own way with default
 * settings common for most senarios.
 * If you want to use and configure Tika yourself, use
 * TikaParser.
 */
@Data
@Accessors(chain = true)
public class DefaultParserConfig {
    //TODO make this an importer config option instead?
    private Path errorsSaveDir;
    // overwrite or add custom parsers.  Needed?? Can add as regular
    // consumer
//    private final Map<ContentType, Consumer<DocContext>>
//            parsers = new HashMap<>();

    //TODO add XFDLParser to list of configs?
    //Maybe keep parsers here, but rename parent class something like
    // TextExtractor, TextConverter, etc..

    private final OCRConfig ocr = new OCRConfig();
    @NonNull
    private final EmbeddedConfig embedded = new EmbeddedConfig();




}
